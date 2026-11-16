package io.lokki.compiler.fir.checkers

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.linked
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaredMemberScope
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.getProperties
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object RuntimeVersionChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) = linked { inspect(expression) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (function.intrinsicKind(context.session) in ENTRY_POINTS) checkRuntimeVersion(expression.source)
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkRuntimeVersion(source: KtSourceElement?) {
    if (runtimeAbi() < BuildConfig.LOKKI_ABI_VERSION) reporter.reportOn(source, LokkiDiagnostics.LOKKI_RUNTIME_TOO_OLD)
}

context(context: CheckerContext)
private fun runtimeAbi(): Int {
    val abi = context.session.symbolProvider.getClassLikeSymbolByClassId(LOKKI_ABI) as? FirRegularClassSymbol ?: return 0
    val version = abi.declaredMemberScope().getProperties(VERSION).singleOrNull() as? FirPropertySymbol
    return ((version?.resolvedInitializer as? FirLiteralExpression)?.value as? Number)?.toInt() ?: 0
}

private val ENTRY_POINTS = setOf(
    IntrinsicKind.MOCK,
    IntrinsicKind.SPY,
    IntrinsicKind.STUB_BLOCK,
    IntrinsicKind.VERIFY_BLOCK,
    IntrinsicKind.EXCLUDE_BLOCK,
    IntrinsicKind.MOCK_OBJECT,
    IntrinsicKind.MOCK_STATIC,
    IntrinsicKind.STATIC_MOCKS,
)

private val LOKKI_ABI = ClassId(FqName("io.lokki.internal"), Name.identifier("LokkiAbi"))
private val VERSION = Name.identifier("VERSION")
