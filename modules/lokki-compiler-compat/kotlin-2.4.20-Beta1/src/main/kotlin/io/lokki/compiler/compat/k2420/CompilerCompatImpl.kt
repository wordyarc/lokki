package io.lokki.compiler.compat.k2420

import io.lokki.compiler.compat.CompilerCompat
import org.jetbrains.kotlin.backend.common.extensions.IrGeneratedDeclarationsRegistrar
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty

internal class CompilerCompatImpl : CompilerCompat {
    override val registersClassesAsMetadataVisible: Boolean
        get() = true

    override fun registerClassAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, irClass: IrClass) {
        registrar.registerClassAsMetadataVisible(irClass)
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
    ): IrDiagnosticReporter.IrDiagnosticContext = reporter.at(element, file)
}
