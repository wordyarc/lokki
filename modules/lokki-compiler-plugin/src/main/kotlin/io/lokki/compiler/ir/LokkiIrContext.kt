package io.lokki.compiler.ir

import io.lokki.compiler.CoreClass
import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.compat.CompilerCompat
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.Mockability
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
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
}

internal class FileContext(
    val ir: IrFile,
    lokki: LokkiIrContext,
    val mockability: Mockability,
    val fields: MockFields,
) {
    val chainedMocks: Boolean = ir.hasAnnotation(CoreClass.CHAINED_MOCKS.id)
    val redirects = RedirectLookup(lokki.pluginContext, ir)
    val diagnostics = IrDiagnostics(lokki, ir)
}
