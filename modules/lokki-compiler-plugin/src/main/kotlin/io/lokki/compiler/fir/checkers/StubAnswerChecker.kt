package io.lokki.compiler.fir.checkers

import io.lokki.compiler.AWAIT_CANCELLATION
import io.lokki.compiler.AnswerNames
import io.lokki.compiler.CoreClass
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.fir.blockArgument
import io.lokki.compiler.fir.classifier
import io.lokki.compiler.fir.intrinsicKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirArgumentList
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenBranch
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.isExhaustive
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkStubAnswer(call: FirFunctionCall, function: FirCallableSymbol<*>, kind: IntrinsicKind) {
    val name = function.name.asString()
    when {
        kind == IntrinsicKind.JUST_AWAIT_BLOCK -> checkCoroutines(call, name)
        kind == IntrinsicKind.STUB_BLOCK && context.dropsValueOf(call) -> reporter.reportOn(
            call.calleeReference.source ?: call.source,
            LokkiDiagnostics.LOKKI_STUB_WITHOUT_ANSWER,
            name,
        )
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkAnswer(answer: FirFunctionCall, function: FirCallableSymbol<*>) {
    when (function.name.asString()) {
        AnswerNames.RETURNS_ARGUMENT -> checkReturnsArgument(answer)
        in AnswerNames.BLOCKS -> {
            checkOriginalCalls(answer)
            checkFieldValues(answer)
        }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkCoroutines(call: FirFunctionCall, name: String) {
    val symbols = context.session.symbolProvider
        .getTopLevelFunctionSymbols(AWAIT_CANCELLATION.packageName, AWAIT_CANCELLATION.callableName)
    if (symbols.isNotEmpty()) return
    reporter.reportOn(
        call.calleeReference.source ?: call.source,
        LokkiDiagnostics.LOKKI_MISSING_COROUTINES_DEPENDENCY,
        name,
    )
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkReturnsArgument(answer: FirFunctionCall) {
    val argument = answer.arguments.singleOrNull()?.unwrapArgument() as? FirLiteralExpression ?: return
    val index = (argument.value as? Number)?.toInt() ?: return
    val (member, count) = answer.stubBlock()?.stubbedMember() ?: return
    if (index in 0..<count) return
    reporter.reportOn(
        argument.source,
        LokkiDiagnostics.LOKKI_RETURNS_ARGUMENT_OUT_OF_RANGE,
        index,
        member.name.asString(),
        count,
    )
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkOriginalCalls(answer: FirFunctionCall) {
    val lambda = answer.arguments.singleOrNull()?.unwrapArgument() as? FirAnonymousFunctionExpression
    val block = lambda?.anonymousFunction
    val scope = block?.receiverParameter?.symbol ?: return
    val (member, _) = answer.stubBlock()?.stubbedMember() ?: return
    if (!member.isAbstract || (member.getContainingClassSymbol() as? FirRegularClassSymbol)?.classKind == ClassKind.ENUM_CLASS) return
    block.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirFunctionCall && element.isOriginalCallIn(scope)) {
                reporter.reportOn(
                    element.calleeReference.source ?: element.source,
                    LokkiDiagnostics.LOKKI_ABSTRACT_ORIGINAL_CALL,
                    member.name.asString(),
                    element.calleeReference.name.asString(),
                )
            }
            element.acceptChildren(this)
        }
    })
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkFieldValues(answer: FirFunctionCall) {
    val lambda = answer.arguments.singleOrNull()?.unwrapArgument() as? FirAnonymousFunctionExpression
    val block = lambda?.anonymousFunction
    val scope = block?.receiverParameter?.symbol ?: return
    val (member, _) = answer.stubBlock()?.stubbedMember() ?: return
    val problem = member.fieldProblem() ?: return
    block.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            if (element is FirQualifiedAccessExpression && element.isFieldValueOf(scope)) {
                reporter.reportOn(
                    element.calleeReference.source ?: element.source,
                    LokkiDiagnostics.LOKKI_FIELD_VALUE_UNAVAILABLE,
                    member.name.asString(),
                    problem,
                )
            }
            element.acceptChildren(this)
        }
    })
}

private fun FirQualifiedAccessExpression.isFieldValueOf(scope: FirBasedSymbol<*>): Boolean {
    val property = calleeReference.toResolvedCallableSymbol() ?: return false
    val receiver = dispatchReceiver?.unwrapSmartcastExpression() as? FirThisReceiverExpression ?: return false
    return property.name.asString() in AnswerNames.FIELD_VALUES &&
        property.callableId?.classId == CoreClass.ANSWER_SCOPE.id &&
        receiver.calleeReference.boundSymbol == scope
}

@OptIn(SymbolInternals::class)
private fun FirCallableSymbol<*>.fieldProblem(): String? {
    val property = unwrapFakeOverrides() as? FirPropertySymbol ?: return "it is not a property"
    val accessors = listOfNotNull(property.getterSymbol, property.setterSymbol)
    return when {
        !property.fir.hasBackingField -> "the property has no backing field"
        accessors.any { it.fir !is FirDefaultPropertyAccessor } ->
            "the property has a custom accessor, whose value differs from the field. Use 'callOriginal()'"
        else -> null
    }
}

context(context: CheckerContext)
private fun FirFunctionCall.stubBlock(): FirAnonymousFunction? {
    var receiver = explicitReceiver?.unwrapSmartcastExpression() as? FirFunctionCall
    while (receiver != null) {
        val callee = receiver.calleeReference.toResolvedCallableSymbol() ?: return null
        receiver = when (callee.intrinsicKind(context.session)) {
            in STUB_BLOCKS ->
                return (receiver.blockArgument(callee) as? FirAnonymousFunctionExpression)?.anonymousFunction
            IntrinsicKind.ANSWER, IntrinsicKind.AWAITS ->
                receiver.explicitReceiver?.unwrapSmartcastExpression() as? FirFunctionCall
            else -> null
        }
    }
    return null
}

context(context: CheckerContext)
private fun FirFunctionCall.isOriginalCallIn(scope: FirBasedSymbol<*>): Boolean {
    val function = calleeReference.toResolvedCallableSymbol() ?: return false
    val receiver = dispatchReceiver?.unwrapSmartcastExpression() as? FirThisReceiverExpression ?: return false
    return function.name.asString() in AnswerNames.ORIGINAL_CALLS &&
        function.intrinsicKind(context.session) == IntrinsicKind.ANSWER &&
        receiver.calleeReference.boundSymbol == scope
}

context(context: CheckerContext)
private fun FirAnonymousFunction.stubbedMember(): Pair<FirCallableSymbol<*>, Int>? {
    val statement = stubbedStatement()
    val call = (if (statement is FirVariableAssignment) statement.lValue else statement) as? FirQualifiedAccessExpression
    val member = call?.takeIf { it.dispatchReceiver != null }?.calleeReference?.toResolvedCallableSymbol() ?: return null
    if (member.intrinsicKind(context.session) == IntrinsicKind.DYNAMIC_CALL) return null
    val regular = when {
        statement is FirVariableAssignment -> 1
        member is FirFunctionSymbol<*> -> member.valueParameterSymbols.size
        else -> 0
    }
    val receiver = if (member.receiverParameterSymbol != null) 1 else 0
    return member to member.contextParameterSymbols.size + receiver + regular
}

private fun CheckerContext.dropsValueOf(expression: FirFunctionCall): Boolean {
    val elements = containingElements
    var value: FirElement = expression
    var index = elements.lastIndex
    while (--index >= 0) {
        val parent = elements[index]
        when (parent) {
            is FirBlock -> if (parent.isUnitCoerced || parent.statements.lastOrNull() !== value) return true
            is FirReturnExpression -> {
                val lambda = (parent.target.labeledElement as? FirAnonymousFunction)?.takeIf { it.isLambda } ?: return false
                index = elements.indexOf(lambda)
                value = lambda
                continue
            }
            is FirArgumentList, is FirWrappedArgumentExpression -> if (value is FirAnonymousFunction) continue else return false
            is FirFunctionCall -> if (value !is FirAnonymousFunction || !parent.returnsResultOf(value)) return false
            is FirSafeCallExpression -> if (parent.selector !== value) return false
            is FirWhenBranch -> if (parent.result !== value) return false
            is FirWhenExpression -> if (!parent.usedAsExpression && !parent.isExhaustive) return true
            is FirCatch -> {}
            is FirTryExpression -> if (parent.finallyBlock === value) return true
            is FirLoop -> return parent.block === value
            is FirFunction, is FirAnonymousInitializer -> return true
            else -> return false
        }
        value = parent
    }
    return false
}

private fun FirFunctionCall.returnsResultOf(lambda: FirAnonymousFunction): Boolean {
    val function = calleeReference.toResolvedCallableSymbol() as? FirFunctionSymbol<*> ?: return false
    val result = function.resolvedReturnType as? ConeTypeParameterType ?: return false
    val parameter = resolvedArgumentMapping?.entries
        ?.find { (argument, _) -> (argument.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction === lambda }
        ?.value
    val returned = parameter?.returnTypeRef?.coneType?.typeArguments?.lastOrNull()?.type as? ConeTypeParameterType
    return returned?.classifier == result.classifier
}
