package io.lokki.compiler

import io.lokki.compiler.fir.LokkiFirExtensionRegistrar
import io.lokki.compiler.ir.LokkiIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
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
        val mockable = configuration.getList(LokkiConfigurationKeys.MOCKABLE).takeIf { it.isNotEmpty() }?.let(::Mockable)
        FirExtensionRegistrarAdapter.registerExtension(LokkiFirExtensionRegistrar(mockable))
        IrGenerationExtension.registerExtension(
            LokkiIrGenerationExtension(configuration[CommonConfigurationKeys.LOOKUP_TRACKER])
        )
    }
}
