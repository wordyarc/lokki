package io.lokki.compiler.compat

import org.jetbrains.kotlin.backend.common.extensions.IrGeneratedDeclarationsRegistrar
import org.jetbrains.kotlin.diagnostics.KtSourcelessDiagnosticFactory
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty

interface CompilerCompat {
    @get:CompatApi(
        since = "2.4.20-Beta1",
        change = "IrGeneratedDeclarationsRegistrar registers classes and properties as metadata-visible",
    )
    val registersClassesAsMetadataVisible: Boolean

    @CompatApi(
        since = "2.4.20-Beta1",
        change = "IrGeneratedDeclarationsRegistrar.registerClassAsMetadataVisible is added",
    )
    fun registerClassAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, irClass: IrClass)

    @CompatApi(
        since = "2.4.20-Beta1",
        change = "IrGeneratedDeclarationsRegistrar.registerPropertyAsMetadataVisible is added",
    )
    fun registerPropertyAsMetadataVisible(registrar: IrGeneratedDeclarationsRegistrar, property: IrProperty)

    @CompatApi(
        since = "2.4.20-Beta1",
        change = "IrDiagnosticReporter.report of a sourceless diagnostic takes CompilerMessageSourceLocation",
    )
    fun reportWithoutSource(reporter: IrDiagnosticReporter, factory: KtSourcelessDiagnosticFactory, message: String)

    @CompatApi(
        since = "2.5.0-Beta2",
        change = "IrDiagnosticReporter.at of an element and its file takes the source element",
    )
    fun diagnosticContext(
        reporter: IrDiagnosticReporter,
        element: IrElement,
        file: IrFile,
    ): IrDiagnosticReporter.IrDiagnosticContext

    interface Factory {
        val minVersion: String

        fun create(): CompilerCompat
    }
}

@Repeatable
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class CompatApi(val since: String, val change: String)
