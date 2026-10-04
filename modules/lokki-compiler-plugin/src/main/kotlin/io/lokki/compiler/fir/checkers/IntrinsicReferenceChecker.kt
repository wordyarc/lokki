package io.lokki.compiler.fir.checkers

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.intrinsicKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallableReferenceAccessChecker
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol

internal object IntrinsicReferenceChecker : FirCallableReferenceAccessChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirCallableReferenceAccess) {
        val function = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (function.intrinsicKind(context.session) == null) return
        reporter.reportOn(
            expression.source,
            LokkiDiagnostics.LOKKI_CALLABLE_REFERENCE_TO_INTRINSIC,
            function.name.asString(),
        )
    }
}
