package io.lokki.compiler

import io.lokki.compiler.compat.CompatLoadException
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.compat.CompilerCompatLoader
import io.lokki.compiler.compat.breaksLinkage
import io.lokki.compiler.fir.LokkiFirExtensionRegistrar
import io.lokki.compiler.ir.LokkiIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.cli.reportLog
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class LokkiCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String
        get() = BuildConfig.COMPILER_PLUGIN_ID
    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        try {
            register(load(configuration), configuration)
        } catch (failure: CompatLoadException) {
            configuration.reportStartFailure(failure)
        }
    }

    private fun load(configuration: CompilerConfiguration): CompilerCompat = try {
        CompilerCompatLoader.load { selection -> configuration.reportLog("$PLUGIN starts. $selection", null) }
    } catch (failure: CompatLoadException) {
        throw if (failure.cause?.breaksLinkage == true) unlinked(failure) else failure
    } catch (failure: LinkageError) {
        if (!failure.breaksLinkage) throw failure
        throw unlinked(failure)
    }

    private fun ExtensionStorage.register(compat: CompilerCompat, configuration: CompilerConfiguration) {
        val mockable = configuration.getList(LokkiConfigurationKeys.MOCKABLE).takeIf { it.isNotEmpty() }
            ?.let(::MockablePatterns)
        val restricted = configuration.getList(LokkiConfigurationKeys.RESTRICTED).takeIf { it.isNotEmpty() }
            ?.let(::MockablePatterns)
        try {
            FirExtensionRegistrarAdapter.registerExtension(LokkiFirExtensionRegistrar(compat, mockable, restricted))
            IrGenerationExtension.registerExtension(
                LokkiIrGenerationExtension(
                    mockable,
                    configuration[CommonConfigurationKeys.LOOKUP_TRACKER],
                    configuration.getBoolean(LokkiConfigurationKeys.DEBUG),
                    compat,
                )
            )
        } catch (failure: LinkageError) {
            if (!failure.breaksLinkage) throw failure
            throw unlinked(failure)
        } catch (failure: ClassCastException) {
            throw unlinked(failure)
        }
    }
}

private fun unlinked(failure: Throwable): CompatLoadException = CompatLoadException(unlinkedCompiler(), failure)

private fun CompilerConfiguration.reportStartFailure(failure: CompatLoadException) {
    val causes = generateSequence(failure.cause, Throwable::cause).joinToString("") { "\nCaused by: $it" }
    val message = "$PLUGIN cannot start. ${failure.message}$causes"
    try {
        report(LokkiDiagnostics.LOKKI_CANNOT_START, message, null)
    } catch (_: LinkageError) {
        get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)?.report(CompilerMessageSeverity.ERROR, message, null)
    }
}
