package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.ExternalMocks
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.Mockability
import io.lokki.compiler.ir.collect.ReifiedHelpers
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.hasAnnotation

internal class LokkiIrContext(
    val pluginContext: IrPluginContext,
    val compat: CompilerCompat,
    val lookupTracker: LookupTracker?,
) {
    val symbols = LokkiSymbols(pluginContext)
    val origin: IrDeclarationOrigin = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)
    val typeSystem: IrTypeSystemContext = IrTypeSystemContextImpl(pluginContext.irBuiltIns)

    fun metadata(declaration: IrDeclaration): ByteArray? =
        pluginContext.metadataDeclarationRegistrar
            .getCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID)

    fun mark(declaration: IrDeclaration): InterceptionMark = InterceptionMark.of(metadata(declaration))
}

internal class FileContext(
    val ir: IrFile,
    val lokki: LokkiIrContext,
    val mockability: Mockability,
    val fields: MockFields,
    val external: ExternalMocks,
    val reifiedHelpers: ReifiedHelpers,
) {
    val chainsMocks: Boolean = ir.hasAnnotation(CoreClass.CHAINED_MOCKS.id)
    val redirects = RedirectLookup(lokki, ir)
    val diagnostics = IrDiagnostics(lokki, ir)
    val failedDynamicCalls = HashSet<IrCall>()
}
