package io.lokki.compiler.fir.checkers

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CONSTRUCTOR_KINDS
import io.lokki.compiler.ENTRY_POINTS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.STATIC_KINDS
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.VERIFY_BLOCKS
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.lokkiEnvironment
import io.lokki.compiler.linked
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol

internal object LokkiCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) = linked { inspect(expression) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(call: FirFunctionCall) {
        val function = call.calleeReference.toResolvedCallableSymbol() ?: return
        val kind = function.intrinsicKind(context.session) ?: return
        if (kind in ENTRY_POINTS) checkRuntimeVersion(call.source)
        when (kind) {
            IntrinsicKind.MOCK, IntrinsicKind.SPY -> checkMock(call, function, kind)
            IntrinsicKind.MOCK_OBJECT -> checkMockObject(call, function)
            in STATIC_KINDS -> checkStaticReferences(call, function)
            in CONSTRUCTOR_KINDS -> checkConstructorClasses(call, function)
            in STUB_BLOCKS -> {
                checkBlock(call, function, kind)
                checkStubAnswer(call, function, kind)
            }
            in VERIFY_BLOCKS -> {
                checkBlock(call, function, kind)
                checkCallCounts(call, function)
            }
            IntrinsicKind.EXCLUDE_BLOCK -> checkBlock(call, function, kind)
            IntrinsicKind.MATCHER -> checkMatcher(call, function)
            IntrinsicKind.ANSWER -> checkAnswer(call, function)
            IntrinsicKind.AWAITS -> checkAwaits(call, "${function.name} Awaits")
            else -> {}
        }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkRuntimeVersion(source: KtSourceElement?) {
    if (context.session.lokkiEnvironment.runtimeAbi < BuildConfig.LOKKI_ABI_VERSION) {
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_RUNTIME_TOO_OLD)
    }
}
