package io.lokki.compiler.fir.checkers

import io.lokki.compiler.LokkiDiagnostics
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.symbols.resolvedAnnotationsWithClassIds

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun reportForeignMock(receiver: FirExpression): Boolean {
    val access = receiver.unwrapSmartcastExpression() as? FirPropertyAccessExpression ?: return false
    val property = access.calleeReference.toResolvedPropertySymbol() ?: return false
    val annotations = property.resolvedAnnotationsWithClassIds +
        property.backingFieldSymbol?.resolvedAnnotationsWithClassIds.orEmpty()
    val (foreign, replacement) = annotations.firstNotNullOfOrNull { annotation ->
        val id = annotation.toAnnotationClassId(context.session)?.asSingleFqName()?.asString()
        id?.let { FOREIGN_MOCKS[it] }?.let { id to it }
    } ?: return false
    reporter.reportOn(receiver.source, LokkiDiagnostics.LOKKI_FOREIGN_MOCK, property.name.asString(), foreign, replacement)
    return true
}

private val FOREIGN_MOCKS = mapOf(
    "org.springframework.boot.test.mock.mockito.MockBean" to "io.lokki.spring.MockBean",
    "org.springframework.boot.test.mock.mockito.SpyBean" to "io.lokki.spring.SpyBean",
    "org.springframework.test.context.bean.override.mockito.MockitoBean" to "io.lokki.spring.MockBean",
    "org.springframework.test.context.bean.override.mockito.MockitoSpyBean" to "io.lokki.spring.SpyBean",
    "com.ninjasquad.springmockk.MockkBean" to "io.lokki.spring.MockBean",
    "com.ninjasquad.springmockk.MockkSpyBean" to "io.lokki.spring.SpyBean",
)
