package io.lokki.compiler.compat.k240

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
        get() = false

    override fun registerClassAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, irClass: IrClass) {
        throw UnsupportedOperationException("Kotlin before 2.4.20 does not register a class as metadata-visible")
    }

    override fun registerPropertyAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, property: IrProperty) {
        throw UnsupportedOperationException("Kotlin before 2.4.20 does not register a property as metadata-visible")
    }

    override fun reportWithoutSource(
        reporter: IrDiagnosticReporter,
        factory: KtSourcelessDiagnosticFactory,
        message: String,
    ) {
        reporter.report(factory, message)
    }

    override fun diagnosticContext(
        reporter: IrDiagnosticReporter,
        element: IrElement,
        file: IrFile,
    ): IrDiagnosticReporter.IrDiagnosticContext = reporter.at(element, file)
}
