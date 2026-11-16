package io.lokki.compiler.fir.checkers

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.interceptionProblem
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkMockObject(call: FirFunctionCall, function: FirCallableSymbol<*>) {
    val parameter = (function as? FirFunctionSymbol<*>)?.valueParameterSymbols?.firstOrNull { it.isVararg } ?: return
    val objects = call.resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key
        as? FirVarargArgumentsExpression ?: return
    for (argument in objects.arguments) {
        if (argument is FirSpreadArgumentExpression) {
            reporter.reportOn(
                argument.source,
                LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE,
                argument.resolvedType.renderReadable(),
                "pass the objects as separate arguments: '${function.name}' does not see the elements of an array",
            )
            continue
        }
        val type = argument.resolvedType.fullyExpandedType()
        val symbol = type.toRegularClassSymbol()
        val reason = when {
            symbol == null || symbol.classKind != ClassKind.OBJECT ->
                "it is not an object: lokki intercepts calls of objects and companion objects, and an instance " +
                    "of an open class is spied with 'spy(Foo(...))'"
            else -> symbol.interceptionProblem()?.let { "it $it" }
        } ?: continue
        val name = symbol?.displayName ?: type.renderReadable()
        reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE, name, reason)
    }
}
