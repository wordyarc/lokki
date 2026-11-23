package io.lokki.compiler.fir.checkers

import io.lokki.compiler.ANY_ORDERING
import io.lokki.compiler.CoreClass
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MatcherNames
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.UNORDERED
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isValueClass
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBooleanOperatorExpression
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.util.OperatorNameConventions

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkCallCounts(verification: FirFunctionCall, function: FirCallableSymbol<*>) {
    val receiver = verification.extensionReceiver
    val (count, problem) = if (receiver != null) {
        val count = Count(receiver, 0)
        if ((count.value ?: 0) >= 0) return
        count to "the count is negative"
    } else {
        val arguments = verification.resolvedArgumentMapping.orEmpty().entries
            .associate { (argument, parameter) -> parameter.name.asString() to argument.unwrapArgument() }
        val exactly = Count(arguments[ParameterNames.EXACTLY], -1)
        val atLeast = Count(arguments[ParameterNames.AT_LEAST], 1)
        val atMost = Count(arguments[ParameterNames.AT_MOST], Int.MAX_VALUE)
        countProblem(exactly, atLeast, atMost)
            ?: orderingProblem(
                arguments[ParameterNames.ORDERING],
                exactly to ParameterNames.EXACTLY,
                atLeast to ParameterNames.AT_LEAST,
                atMost to ParameterNames.AT_MOST,
            )
            ?: return
    }
    val source = (count.argument ?: verification).source
    val name = function.name.asString()
    reporter.reportOn(source, LokkiDiagnostics.LOKKI_CONFLICTING_VERIFY_ARGUMENTS, name, problem)
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkMatcher(matcher: FirFunctionCall, function: FirCallableSymbol<*>) {
    when (val name = function.name.asString()) {
        in MatcherNames.WITH_ARG -> checkWithArg(matcher, name)
        in MatcherNames.REF_EQ -> checkRefEq(matcher, name)
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkRefEq(matcher: FirFunctionCall, name: String) {
    val type = matcher.typeArguments.firstOrNull()?.toConeTypeProjection()?.type ?: return
    if (!type.isValueClass(context.session)) return
    reporter.reportOn(
        matcher.calleeReference.source ?: matcher.source,
        LokkiDiagnostics.LOKKI_REF_EQ_OF_VALUE_CLASS,
        name,
        type.renderReadable(),
    )
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkWithArg(matcher: FirFunctionCall, name: String) {
    val block = matcher.arguments.lastOrNull()?.unwrapArgument() as? FirAnonymousFunctionExpression ?: return
    val result = block.anonymousFunction.body?.statements?.lastOrNull() ?: return
    if (!result.isPredicate) return
    reporter.reportOn(result.source, LokkiDiagnostics.LOKKI_WITH_ARG_PREDICATE_NOT_USED, name)
}

private class Count(val argument: FirExpression?, private val default: Int) {
    val value: Int? =
        if (argument == null) default else ((argument as? FirLiteralExpression)?.value as? Number)?.toInt()

    val isExplicit: Boolean
        get() = value.differsFrom(default)
}

private fun countProblem(exactly: Count, atLeast: Count, atMost: Count): Pair<Count, String>? {
    val least = atLeast.value
    val most = atMost.value
    return when {
        exactly.value.differsFrom(-1) && (least.differsFrom(1) || most.differsFrom(Int.MAX_VALUE)) ->
            exactly to "'exactly' cannot be combined with 'atLeast' or 'atMost'"
        (exactly.value ?: 0) < -1 -> exactly to "'exactly' is negative"
        (least ?: 0) < 0 -> atLeast to "'atLeast' is negative"
        (most ?: 0) < 0 -> atMost to "'atMost' is negative"
        least == null || most == null || least <= most -> null
        atLeast.argument == null -> atMost to
            "'atMost' is $most, but 'atLeast' is 1 by default. Use 'exactly = 0' to verify that there were no calls"
        else -> atMost to "'atLeast' ($least) is greater than 'atMost' ($most)"
    }
}

private fun orderingProblem(ordering: FirExpression?, vararg counts: Pair<Count, String>): Pair<Count, String>? {
    val entry = (ordering as? FirQualifiedAccessExpression)?.calleeReference?.toResolvedEnumEntrySymbol() ?: return null
    if (entry.callableId.classId != CoreClass.ORDERING.id) return null
    if (entry.name.asString() == UNORDERED || entry.name.asString() == ANY_ORDERING) return null
    val (count, name) = counts.firstOrNull { (count, _) -> count.isExplicit } ?: return null
    return count to "'$name' is only allowed with Ordering.$UNORDERED or Ordering.$ANY_ORDERING, but 'ordering' is Ordering.${entry.name}"
}

private fun Int?.differsFrom(value: Int): Boolean = this != null && this != value

private val FirStatement.isPredicate: Boolean
    get() = when (this) {
        is FirEqualityOperatorCall, is FirComparisonExpression, is FirBooleanOperatorExpression -> true
        is FirTypeOperatorCall -> operation == FirOperation.IS || operation == FirOperation.NOT_IS
        is FirFunctionCall -> origin == FirFunctionCallOrigin.Operator && calleeReference.name in PREDICATE_OPERATORS
        is FirLiteralExpression -> kind == ConstantValueKind.Boolean
        else -> false
    }

private val PREDICATE_OPERATORS = setOf(OperatorNameConventions.CONTAINS, OperatorNameConventions.NOT)
