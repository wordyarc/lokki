package io.lokki.compiler.compat.k250

import io.lokki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.extensions.IrGeneratedDeclarationsRegistrar
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.serialization.providedDeclarationsForMetadataService
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.classIdOrFail

internal class CompilerCompatImpl : CompilerCompat {
    override val registersClassesAsMetadataVisible: Boolean
        get() = true

    override fun registerClassAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, irClass: IrClass) {
        registrar.registerClassAsMetadataVisible(irClass)
        irClass.reattachMembersOfStaleClass()
    }

    override fun registerPropertyAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, property: IrProperty) {
        registrar.registerPropertyAsMetadataVisible(property)
    }

    override fun reportWithoutSource(
        reporter: IrDiagnosticReporter,
        factory: KtSourcelessDiagnosticFactory,
        message: String,
    ) {
        reporter.report(factory, message, location = null)
    }

    override fun diagnosticContext(
        reporter: IrDiagnosticReporter,
        element: IrElement,
        file: IrFile,
    ): IrDiagnosticReporter.IrDiagnosticContext = reporter.at(element, file, sourceElement = null)
}

// Костыль т.к. Fir2IrIrGeneratedDeclarationsRegistrar ищет класс для членов сначала в classpath, а потом уже в
// irClass.metadata. Если в classpath лежит этот же класс от прошлой сборки (Maven кладёт target/classes в classpath
// main), члены регистрируются под старым классом, и новый уходит в метаданные пустым. Так в 2.4.20 и 2.5.0-RC.
@OptIn(SymbolInternals::class, UnsafeDuringIrConstructionAPI::class)
private fun IrClass.reattachMembersOfStaleClass() {
    val fir = (metadata as? FirMetadataSource.Class)?.fir ?: return
    val session = fir.moduleData.session
    val stale = session.symbolProvider.getClassLikeSymbolByClassId(classIdOrFail)?.fir
    if (stale == null || stale === fir) return
    val provided = session.providedDeclarationsForMetadataService
    for (member in declarations) {
        val source = when (member) {
            is IrProperty -> member.metadata
            is IrSimpleFunction -> member.metadata.takeIf { member.correspondingPropertySymbol == null }
            else -> null
        }
        (source as? FirMetadataSource)?.let { provided.registerDeclaration(it.fir, fir) }
    }
}
