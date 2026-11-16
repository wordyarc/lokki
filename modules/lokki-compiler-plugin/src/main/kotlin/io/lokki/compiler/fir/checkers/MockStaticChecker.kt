package io.lokki.compiler.fir.checkers

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.MemberInterception
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.staticInterception
import io.lokki.compiler.linked
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

internal object MockStaticChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) = linked { inspect(expression) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() as? FirFunctionSymbol<*> ?: return
        val kind = function.intrinsicKind(context.session)
        if (kind != IntrinsicKind.MOCK_STATIC && kind != IntrinsicKind.STATIC_MOCKS) return
        val name = function.name.asString()
        for ((argument, parameter) in expression.resolvedArgumentMapping.orEmpty()) {
            if (parameter.name.asString() !in REFERENCE_PARAMETERS) continue
            val references = (argument as? FirVarargArgumentsExpression)?.arguments ?: listOf(argument)
            references.forEach { check(it, name) }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun check(argument: FirExpression, function: String) {
        val reference = argument as? FirCallableReferenceAccess
        val receiver = reference?.explicitReceiver
        val bound = receiver != null && receiver !is FirResolvedQualifier
        if (argument is FirSpreadArgumentExpression || reference == null || bound) {
            reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_REFERENCE_NOT_LITERAL, function)
            return
        }
        val callee = reference.calleeReference.toResolvedCallableSymbol() ?: return
        val reason = when (val interception = callee.staticInterception()) {
            MemberInterception.Intercepted -> return
            is MemberInterception.NotIntercepted -> interception.reason
            MemberInterception.NotObjectMember -> memberReason(callee)
        }
        val name = callee.name.asString()
        reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE, name, reason)
    }

    context(context: CheckerContext)
    private fun memberReason(callee: FirCallableSymbol<*>): String {
        val owner = callee.getContainingClassSymbol() as? FirRegularClassSymbol
        return when {
            owner == null -> "it is local"
            callee.isStatic -> "it is a static member of a Java class, which lokki does not intercept"
            owner.classKind == ClassKind.OBJECT ->
                "it is a member of '${owner.displayName}': mock the object with 'mockObject(${owner.name})'"
            else -> "it is a member of '${owner.displayName}': mock its instance with 'mock<T>()' or 'spy(...)'"
        }
    }
}

private val REFERENCE_PARAMETERS = setOf("function", "functions", "property", "properties")
