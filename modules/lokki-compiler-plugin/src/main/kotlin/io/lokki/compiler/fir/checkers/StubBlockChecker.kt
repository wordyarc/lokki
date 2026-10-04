package io.lokki.compiler.fir.checkers

import io.lokki.compiler.ALL_ANY
import io.lokki.compiler.COMPOSITE_MATCHERS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isFinalType
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isMethodOfAny
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirCheckNotNullCall
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.argument
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirLocalPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

internal object StubBlockChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() as? FirFunctionSymbol<*> ?: return
        val mode = when (function.intrinsicKind(context.session)) {
            IntrinsicKind.STUB_BLOCK -> BlockMode.STUB
            IntrinsicKind.VERIFY_BLOCK -> BlockMode.VERIFY
            IntrinsicKind.EXCLUDE_BLOCK -> BlockMode.EXCLUDE
            else -> return
        }
        val parameter = function.valueParameterSymbols.lastOrNull() ?: return
        val argument = expression.resolvedArgumentMapping?.entries
            ?.find { it.value.symbol == parameter }
            ?.key?.unwrapArgument()
            ?: return
        val name = function.name.asString()
        val block = (argument as? FirAnonymousFunctionExpression)?.anonymousFunction
        if (block == null) {
            reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_LITERAL, name)
        } else {
            StubBlock(name, mode, block, context, reporter).check()
        }
    }
}

private enum class BlockMode(val action: String) {
    STUB("Stub"),
    VERIFY("Verify"),
    EXCLUDE("Exclude"),
}

private class StubBlock(
    private val function: String,
    private val mode: BlockMode,
    private val block: FirAnonymousFunction,
    private val context: CheckerContext,
    private val reporter: DiagnosticReporter,
) {
    private val session = context.session
    private val scope = block.receiverParameter?.symbol
    private val action = mode.action

    fun check() {
        if (mode != BlockMode.STUB) {
            val statements = block.verifiedStatements()
            if (statements.isEmpty()) notSingleCall(block, "is empty") else statements.forEach(::checkStatement)
            return
        }
        val statement = block.stubbedStatement() ?: return notSingleCall(
            block,
            if (block.body?.statements.isNullOrEmpty()) "is empty" else "has several statements",
        )
        if (statement.source?.kind is KtFakeSourceElementKind.ImplicitUnit) {
            notSingleCall(block, "is empty")
        } else {
            checkStatement(statement)
        }
    }

    private fun checkStatement(statement: FirStatement) {
        when {
            statement.isCompoundAssignment -> notSingleCall(
                statement,
                "is a compound assignment, which reads and writes the property. " +
                    "$action the setter with a plain assignment of a matcher",
            )
            statement is FirSafeCallExpression ->
                notSingleCall(statement, "is a safe call. Use '!!' on the receiver instead of '?.'")
            statement is FirEqualityOperatorCall ->
                notSingleCall(statement, "compares values with an operator${callExplicitly("equals")}")
            statement is FirComparisonExpression ->
                notSingleCall(statement, "compares values with an operator. Call 'compareTo' explicitly")
            statement is FirStringConcatenationCall ->
                notSingleCall(statement, "is a string template${callExplicitly("toString")}")
            statement is FirVariableAssignment -> when (val property = statement.lValue) {
                is FirQualifiedAccessExpression -> checkCall(statement, property, listOf(statement.rValue))
                else -> notSingleCall(statement, "is not a call")
            }
            statement is FirFunctionCall -> checkCall(statement, statement, statement.arguments)
            statement is FirPropertyAccessExpression -> checkCall(statement, statement, emptyList())
            mode == BlockMode.VERIFY && statement.isLoop -> notSingleCall(
                statement,
                "is a loop. Call 'verify' in the loop or verify one call with matchers and 'exactly'",
            )
            mode == BlockMode.VERIFY && statement is FirWhenExpression ->
                notSingleCall(statement, "is a condition. Call 'verify' in its branches")
            else -> notSingleCall(statement, "is not a call")
        }
    }

    private fun checkCall(statement: FirStatement, call: FirQualifiedAccessExpression, arguments: List<FirExpression>) {
        val callee = call.calleeReference.toResolvedCallableSymbol() ?: return
        val receiver = call.dispatchReceiver?.unwrapSmartcastExpression()
        when {
            mode == BlockMode.VERIFY && callee.intrinsicKind(session) == IntrinsicKind.WAS_NOT_CALLED &&
                receiver.isScope() -> checkNoCalls(call)
            call.takesScope() -> when {
                !receiver.isScope() || callee.intrinsicKind(session) == IntrinsicKind.MATCHER ->
                    report(statement, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION)
                else -> notInterceptable(
                    call,
                    callee,
                    "it is a member of the matcher scope, not of a mock. Write the receiver of the call explicitly",
                )
            }
            callee is FirValueParameterSymbol || callee is FirLocalPropertySymbol ->
                notSingleCall(statement, "is not a call")
            callee.isStatic -> notInterceptable(call, callee, "it is static")
            receiver != null -> checkMemberCall(call, callee, receiver, arguments)
            arguments.any { it.unwrapArgument() is FirAnonymousFunctionExpression } ->
                notSingleCall(statement, "wraps the call into a lambda. Move the wrapping call outside the block")
            call.extensionReceiver != null ->
                notInterceptable(call, callee, "it is an extension, not a member. $action the member it calls")
            else -> notInterceptable(call, callee, "it is not a member")
        }
    }

    private fun checkMemberCall(
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        receiver: FirExpression,
        arguments: List<FirExpression>,
    ) {
        val matchers = receiver.scopeCalls()
        if (matchers.singleOrNull() === receiver) {
            report(receiver, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION)
        } else {
            matchers.forEach { report(it, LokkiDiagnostics.LOKKI_CHAINED_CALL_DISABLED) }
        }
        val reason = notInterceptableReason(callee, receiver)
        val notRecorded = when (mode) {
            BlockMode.STUB -> null
            BlockMode.VERIFY -> LokkiDiagnostics.LOKKI_NOT_RECORDED
            BlockMode.EXCLUDE -> LokkiDiagnostics.LOKKI_USELESS_EXCLUSION
        }
        when {
            reason != null -> notInterceptable(call, callee, reason)
            notRecorded != null && callee is FirNamedFunctionSymbol && callee.isMethodOfAny -> reporter.reportOn(
                call.calleeReference.source ?: call.source,
                notRecorded,
                callee.name.asString(),
                context,
            )
        }
        val first = firstArgument(call, callee, arguments)
        (listOfNotNull(call.extensionReceiver) + call.contextArguments + arguments).forEach { checkArgument(it, first) }
    }

    private fun firstArgument(
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        arguments: List<FirExpression>,
    ): FirExpression? {
        val parameter = (callee as? FirFunctionSymbol<*>)?.valueParameterSymbols?.firstOrNull()
        val regular = when (call) {
            is FirCall -> call.resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key
            else -> arguments.firstOrNull()
        }
        val first = (call.contextArguments.firstOrNull() ?: call.extensionReceiver ?: regular)?.unwrapArgument()
        return (first as? FirVarargArgumentsExpression)?.arguments?.firstOrNull()?.unwrapArgument() ?: first
    }

    private fun checkNoCalls(call: FirQualifiedAccessExpression) {
        val mocks = call.extensionReceiver ?: return
        misplacedMatchers(mocks)
        val result = mocks.unwrapped(block) as? FirFunctionCall ?: return
        if (result.explicitReceiver == null || result.dispatchReceiver == null) return
        val member = result.calleeReference.toResolvedCallableSymbol() ?: return
        reporter.reportOn(
            mocks.source,
            LokkiDiagnostics.LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT,
            member.name.asString(),
            context,
        )
    }

    private fun notInterceptableReason(callee: FirCallableSymbol<*>, receiver: FirExpression): String? {
        val inline = if (callee is FirPropertySymbol) callee.getterSymbol?.isInline == true else callee.isInline
        return when {
            callee is FirFieldSymbol -> "it is a field"
            inline -> "it is inline. $action the member it calls"
            callee.modality == Modality.FINAL -> "it is final"
            else -> receiver.resolvedType.fullyExpandedType(session).toRegularClassSymbol(session)
                ?.takeIf { it.isFinalType }
                ?.let { "its receiver type '${it.displayName}' is final" }
        }
    }

    private fun checkArgument(argument: FirExpression, first: FirExpression?) {
        val expression = argument.unwrapArgument()
        when {
            expression is FirVarargArgumentsExpression -> expression.arguments.forEach { element ->
                when {
                    element !is FirSpreadArgumentExpression -> checkArgument(element, first)
                    !element.expression.takesScope() -> misplacedMatchers(element.expression)
                }
            }
            expression is FirQualifiedAccessExpression && expression.takesScope() -> {
                val matcher = expression.calleeReference.toResolvedCallableSymbol()
                    ?.takeIf { it.intrinsicKind(session) == IntrinsicKind.MATCHER }
                    ?.name?.asString()
                if (matcher == ALL_ANY && expression !== first) {
                    report(expression, LokkiDiagnostics.LOKKI_ALL_ANY_NOT_FIRST)
                }
                val operands = expression.operands().filterNot { it.isScope() }
                operands.forEach { if (matcher in COMPOSITE_MATCHERS) checkArgument(it, first) else misplacedMatchers(it) }
            }
            else -> misplacedMatchers(expression)
        }
    }

    private fun misplacedMatchers(expression: FirExpression) {
        expression.scopeCalls().forEach { report(it, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION) }
    }

    private fun FirElement.scopeCalls(): List<FirElement> {
        val calls = ArrayList<FirElement>()
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element.takesScope()) calls += element else element.acceptChildren(this)
            }
        })
        return calls
    }

    private fun FirElement.takesScope(): Boolean =
        this is FirQualifiedAccessExpression && operands().any { it.isScope() }

    private fun FirQualifiedAccessExpression.operands(): List<FirExpression> =
        listOfNotNull(dispatchReceiver, extensionReceiver) + contextArguments + (this as? FirCall)?.arguments.orEmpty()

    private fun FirExpression?.isScope(): Boolean {
        val receiver = this?.unwrapArgument()?.unwrapSmartcastExpression() as? FirThisReceiverExpression
        return scope != null && receiver?.calleeReference?.boundSymbol == scope
    }

    private fun callExplicitly(member: String): String =
        if (mode == BlockMode.STUB) ". Call '$member' explicitly" else ""

    private fun notSingleCall(element: FirElement, reason: String) {
        val requirement =
            if (mode == BlockMode.STUB) "be a single call of a mock member" else "consist of calls of mock members"
        reporter.reportOn(
            element.source ?: block.source,
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_SINGLE_CALL,
            function,
            requirement,
            reason,
            context,
        )
    }

    private fun notInterceptable(call: FirQualifiedAccessExpression, callee: FirCallableSymbol<*>, reason: String) {
        val source = call.calleeReference.source ?: call.source ?: block.source
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_NOT_INTERCEPTABLE, callee.name.asString(), reason, context)
    }

    private fun report(element: FirElement, factory: KtDiagnosticFactory0) {
        reporter.reportOn(element.source ?: block.source, factory, context)
    }
}

internal fun FirAnonymousFunction.stubbedStatement(): FirStatement? =
    body?.statements.orEmpty()
        .singleOrNull { it.source?.kind !is KtFakeSourceElementKind.ImplicitUnit.IndexedAssignmentCoercion }
        ?.unwrapped(this)

private fun FirAnonymousFunction.verifiedStatements(): List<FirStatement> =
    body?.statements.orEmpty()
        .map { it.unwrapped(this) }
        .filter { it.source?.kind !is KtFakeSourceElementKind.ImplicitUnit }

private fun FirStatement.unwrapped(block: FirAnonymousFunction): FirStatement {
    var statement = this
    while (true) {
        statement = when {
            statement is FirReturnExpression && statement.target.labeledElement.symbol == block.symbol ->
                statement.result
            statement is FirTypeOperatorCall && statement.operation == FirOperation.AS -> statement.argument
            statement is FirCheckNotNullCall -> statement.argument
            statement is FirSmartCastExpression -> statement.originalExpression
            else -> return statement
        }
    }
}

private val FirStatement.isLoop: Boolean
    get() = this is FirLoop || this is FirBlock && statements.lastOrNull() is FirLoop

private val FirStatement.isCompoundAssignment: Boolean
    get() = when (this) {
        is FirVariableAssignment -> source?.kind is KtFakeSourceElementKind.DesugaredAugmentedAssign
        is FirBlock -> statements.any { it is FirVariableAssignment }
        else -> false
    }
