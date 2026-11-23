package io.lokki.compiler.fir.checkers

import io.lokki.compiler.CHILD_MOCK_NEEDED
import io.lokki.compiler.CoreClass
import io.lokki.compiler.DynamicNames
import io.lokki.compiler.FINALIZER_REASON
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.MatcherNames
import io.lokki.compiler.ORDERED_VERIFY_BLOCKS
import io.lokki.compiler.SLOT_READS
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.VERIFY_BLOCKS
import io.lokki.compiler.WITH_CONTEXT
import io.lokki.compiler.fir.MemberInterception
import io.lokki.compiler.fir.argument
import io.lokki.compiler.fir.blockArgument
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isClassFinalizer
import io.lokki.compiler.fir.isFinalType
import io.lokki.compiler.fir.isMemberOfAny
import io.lokki.compiler.fir.isOpenedInBytecode
import io.lokki.compiler.fir.objectInterception
import io.lokki.compiler.fir.staticInterception
import io.lokki.compiler.linked
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isOperator
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirCheckNotNullCall
import org.jetbrains.kotlin.fir.expressions.FirCheckedSafeCallSubject
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirEqualityOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSafeCallExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirStringConcatenationCall
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.argument
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.expressions.unwrapSmartcastExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirLocalPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.OperatorNameConventions

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkBlock(call: FirFunctionCall, function: FirCallableSymbol<*>, kind: IntrinsicKind) {
    val mode = when (kind) {
        in STUB_BLOCKS -> BlockMode.STUB
        in VERIFY_BLOCKS -> BlockMode.VERIFY
        else -> BlockMode.EXCLUDE
    }
    val argument = call.blockArgument(function) ?: return
    val name = function.name.asString()
    val block = (argument as? FirAnonymousFunctionExpression)?.anonymousFunction
    if (block == null) {
        reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_LITERAL, name)
    } else {
        StubBlock(name, mode, kind in ORDERED_VERIFY_BLOCKS, block, context, reporter).check()
    }
}

private enum class BlockMode(val action: String) {
    STUB("Stub"),
    VERIFY("Verify"),
    EXCLUDE("Exclude"),
}

private class Requirement(val blockName: String, val text: String)

private class StubBlock(
    private val function: String,
    private val mode: BlockMode,
    private val ordered: Boolean,
    private val block: FirAnonymousFunction,
    private val context: CheckerContext,
    private val reporter: DiagnosticReporter,
) {
    private val session = context.session
    private val scope = block.receiverParameter?.symbol
    private val action = mode.action
    private val contexts = HashSet<FirCallableSymbol<*>>()
    private val contextSources = HashMap<FirElement, KtSourceElement?>()

    fun check() {
        if (mode != BlockMode.EXCLUDE) checkSlotReads()
        if (mode == BlockMode.STUB) return Requirement(function, SINGLE_CALL).checkSingleCall(block)
        val requirement = Requirement(function, "consist of calls of mock members")
        val statements = block.verifiedStatements()
        if (statements.isEmpty()) {
            requirement.notSingleCall(block, "is empty")
        } else {
            statements.forEach { requirement.checkStatement(it) }
        }
    }

    private fun Requirement.checkSingleCall(lambda: FirAnonymousFunction) {
        val statement = lambda.stubbedStatement() ?: return notSingleCall(
            lambda,
            if (lambda.body?.statements.isNullOrEmpty()) "is empty" else "has several statements",
        )
        if (statement.source?.kind is KtFakeSourceElementKind.ImplicitUnit) {
            notSingleCall(lambda, "is empty")
        } else {
            checkStatement(statement)
        }
    }

    private fun checkContextBlock(call: FirFunctionCall) {
        val argument = call.arguments.lastOrNull()?.unwrapArgument() ?: return
        val lambda = (argument as? FirAnonymousFunctionExpression)?.anonymousFunction ?: return reporter.reportOn(
            argument.source,
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_LITERAL,
            WITH_CONTEXT,
            context,
        )
        contexts += lambda.contextParameters.map { it.symbol }
        lambda.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirQualifiedAccessExpression) {
                    element.contextArguments.filter { it.isContextValue() }
                        .forEach { contextSources[it] = element.source }
                }
                element.acceptChildren(this)
            }
        })
        Requirement(WITH_CONTEXT, SINGLE_CALL).checkSingleCall(lambda)
    }

    private fun Requirement.checkStatement(statement: FirStatement) {
        when {
            statement.isCompoundAssignment -> notSingleCall(
                statement,
                "is a compound assignment, which reads and writes the property. " +
                    "$action the setter with a plain assignment of a matcher",
            )
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
            statement is FirFunctionCall && statement.isContextBlock() -> checkContextBlock(statement)
            statement.isConstructed() -> if (mode != BlockMode.VERIFY) notSingleCall(statement, "is not a call of a member")
            statement is FirFunctionCall -> checkCall(statement, statement, statement.arguments)
            statement is FirPropertyAccessExpression -> checkCall(statement, statement, emptyList())
            mode == BlockMode.VERIFY && statement.isLoop -> notSingleCall(
                statement,
                when {
                    ordered -> "is a loop. Write out the calls in the expected order"
                    else -> "is a loop. Call 'verify' in the loop or verify one call with matchers and 'exactly'"
                },
            )
            mode == BlockMode.VERIFY && statement is FirWhenExpression -> notSingleCall(
                statement,
                "is a condition. Call '${if (ordered) function else "verify"}' in its branches",
            )
            else -> notSingleCall(statement, "is not a call")
        }
    }

    private fun Requirement.checkCall(
        statement: FirStatement,
        call: FirQualifiedAccessExpression,
        arguments: List<FirExpression>,
    ) {
        val callee = call.calleeReference.toResolvedCallableSymbol() ?: return
        val receiver = call.dispatchReceiver?.unwrapSmartcastExpression()
        when {
            call.configuresStub() -> notSingleCall(statement, "configures a nested stub. Move it outside the block")
            mode == BlockMode.VERIFY && callee.intrinsicKind(session) == IntrinsicKind.WAS_NOT_CALLED &&
                receiver.isScope() -> checkNoCalls(statement, call)
            callee.intrinsicKind(session) == IntrinsicKind.DYNAMIC_CALL ->
                checkDynamicCall(statement, call, callee, receiver, arguments)
            callee.intrinsicKind(session) == IntrinsicKind.DYNAMIC_MEMBER ->
                notSingleCall(statement, "names a member without calling it. Add the arguments: 'mock[\"name\"]()'")
            call.takesScope() -> when {
                !receiver.isScope() || callee.intrinsicKind(session) == IntrinsicKind.MATCHER ->
                    matcherInCallPosition(statement)
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
            with(context) { callee.staticInterception() } == MemberInterception.Intercepted ->
                checkArguments(call, callee, arguments)
            arguments.any { it.unwrapArgument() is FirAnonymousFunctionExpression } -> notSingleCall(
                statement,
                "wraps the call into a lambda. " + when {
                    callee.callableId?.asSingleFqName() in COROUTINE_BUILDERS -> coroutineAdvice(callee)
                    else -> "Move the wrapping call outside the block" +
                        " or match its context with 'withContext'".takeIf { arguments.any { it.hasContextLambda() } }
                            .orEmpty()
                },
            )
            call.extensionReceiver != null ->
                notInterceptable(call, callee, "it is an extension, not a member. $action the member it calls")
            else -> notInterceptable(call, callee, "it is not a member")
        }
    }

    private fun FirQualifiedAccessExpression.configuresStub(): Boolean =
        listOfNotNull(dispatchReceiver, extensionReceiver).any { receiver ->
            receiver.resolvedType.fullyExpandedType(session).toRegularClassSymbol(session)?.classId in STUB_SCOPES
        }

    private fun Requirement.checkDynamicCall(
        statement: FirStatement,
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        access: FirExpression?,
        arguments: List<FirExpression>,
    ) {
        val member = (access?.unwrapArgument() as? FirFunctionCall)?.takeIf {
            it.calleeReference.toResolvedCallableSymbol()?.intrinsicKind(session) == IntrinsicKind.DYNAMIC_MEMBER &&
                it.dispatchReceiver.isScope()
        }
        val target = member?.extensionReceiver ?: call.extensionReceiver?.takeIf { access.isScope() }
            ?: return notSingleCall(statement, "calls a dynamic member that is not written in this call")
        checkReceiverMatchers(target)
        val values = when (callee.name.asString()) {
            DynamicNames.WITH_ARGUMENTS -> arguments.flatMap { it.listLiteralElements() ?: listOf(it) }
            else -> arguments
        }
        checkArguments(call, callee, values)
    }

    private fun FirExpression.listLiteralElements(): List<FirExpression>? {
        val list = unwrapArgument() as? FirFunctionCall ?: return null
        val factory = list.calleeReference.toResolvedCallableSymbol()?.callableId?.asSingleFqName()
        if (factory !in LIST_FACTORIES) return null
        return list.arguments.flatMap { (it.unwrapArgument() as? FirVarargArgumentsExpression)?.arguments ?: listOf(it) }
    }

    private fun checkReceiverMatchers(receiver: FirExpression) {
        val matchers = receiver.scopeCalls()
        when {
            matchers.singleOrNull() === receiver -> matcherInCallPosition(receiver)
            else -> {
                val linked = receiver.links().filterNot { it.isConstructed() }.flatMap(::checkLink)
                matchers.filter { it !in linked }
                    .forEach { report(it, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION) }
            }
        }
    }

    private fun coroutineAdvice(builder: FirCallableSymbol<*>): String =
        when {
            function.startsWith("co") -> "Remove '${builder.name}': '$function' takes suspend calls"
            else -> "Use 'co${function.replaceFirstChar(Char::uppercaseChar)}' instead of '${builder.name}': " +
                "it takes suspend calls"
        }

    private fun checkMemberCall(
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        receiver: FirExpression,
        arguments: List<FirExpression>,
    ) {
        checkReceiverMatchers(receiver)
        val reason = notInterceptableReason(callee, receiver)
        val notRecorded = when (mode) {
            BlockMode.STUB -> null
            BlockMode.VERIFY -> LokkiDiagnostics.LOKKI_NOT_RECORDED
            BlockMode.EXCLUDE -> LokkiDiagnostics.LOKKI_USELESS_EXCLUSION
        }
        when {
            reason != null -> notInterceptable(call, callee, reason)
            notRecorded != null && callee is FirNamedFunctionSymbol && callee.isMemberOfAny -> reporter.reportOn(
                call.calleeReference.source ?: call.source,
                notRecorded,
                callee.name.asString(),
                context,
            )
        }
        checkArguments(call, callee, arguments)
    }

    private fun checkArguments(
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        arguments: List<FirExpression>,
    ) {
        val first = firstArgument(call, callee, arguments)
        (listOfNotNull(call.extensionReceiver) + call.contextArguments + arguments).forEach { checkArgument(it, first) }
    }

    private fun checkLink(link: FirQualifiedAccessExpression): List<FirElement> {
        val arguments = listOfNotNull(link.extensionReceiver) + link.contextArguments +
            (link as? FirCall)?.arguments.orEmpty()
        val matchers = arguments.flatMap { it.scopeCalls() }
        if (matchers.isEmpty()) return matchers
        val callee = link.calleeReference.toResolvedCallableSymbol() ?: return matchers
        val reason = notInterceptableReason(callee, link.dispatchReceiver!!)
            ?: "it is a member of 'Any'".takeIf { callee is FirNamedFunctionSymbol && callee.isMemberOfAny }
        if (reason != null) {
            notInterceptable(link, callee, reason)
            return matchers
        }
        with(context) { link.resolvedType.childMockProblem() }?.let { (type, problem) ->
            reporter.reportOn(
                link.source,
                LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE,
                type,
                "$problem. $CHILD_MOCK_NEEDED",
                context,
            )
        }
        val first = firstArgument(link, callee, (link as? FirCall)?.arguments.orEmpty())
        arguments.forEach { checkArgument(it, first) }
        return matchers
    }

    private fun firstArgument(
        call: FirQualifiedAccessExpression,
        callee: FirCallableSymbol<*>,
        arguments: List<FirExpression>,
    ): FirExpression? {
        val parameter = (callee as? FirFunctionSymbol<*>)?.valueParameterSymbols?.firstOrNull()
        val regular = when (call) {
            is FirCall -> parameter?.let { call.argument(it) }
            else -> arguments.firstOrNull()
        }
        val first = (call.contextArguments.firstOrNull() ?: call.extensionReceiver ?: regular)?.unwrapArgument()
        return (first as? FirVarargArgumentsExpression)?.arguments?.firstOrNull()?.unwrapArgument() ?: first
    }

    private fun Requirement.checkNoCalls(statement: FirStatement, call: FirQualifiedAccessExpression) {
        val mocks = call.extensionReceiver ?: return
        val pattern = mocks.unwrapped(block) as? FirQualifiedAccessExpression
        val arguments = (pattern as? FirCall)?.arguments.orEmpty()
        val callee = pattern?.calleeReference?.toResolvedCallableSymbol()
        val intercepted = callee != null && callee.intrinsicKind(session) != IntrinsicKind.DYNAMIC_CALL &&
            !pattern.takesScope() && (
            pattern.dispatchReceiver != null ||
                with(context) { callee.staticInterception() } == MemberInterception.Intercepted
            )
        val matched = intercepted &&
            (arguments.any { it.scopeCalls().isNotEmpty() } || pattern.dispatchReceiver?.scopeCalls().orEmpty().isNotEmpty())
        if (matched) checkCall(statement, pattern, arguments) else misplacedMatchers(mocks)
    }

    private fun checkSlotReads() {
        val captured = HashSet<List<FirBasedSymbol<*>>>()
        val reads = ArrayList<Pair<FirQualifiedAccessExpression, List<FirBasedSymbol<*>>>>()
        block.accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirQualifiedAccessExpression) {
                    val callee = element.calleeReference.toResolvedCallableSymbol()
                    val name = callee?.name?.asString()
                    when {
                        callee?.callableId?.classId == CoreClass.CAPTURING_SLOT.id && name in SLOT_READS ->
                            element.dispatchReceiver?.slotPath(session)?.let { reads += element to it }
                        name in MatcherNames.CAPTURE && callee?.intrinsicKind(session) == IntrinsicKind.MATCHER ->
                            (element as? FirFunctionCall)?.arguments?.singleOrNull()?.slotPath(session)?.let(captured::add)
                    }
                }
                element.acceptChildren(this)
            }
        })
        val hint = when (mode) {
            BlockMode.STUB -> "Read the slot in the answer or after the call"
            else -> "Capture in one verification and read the slot in the next one"
        }
        for ((read, path) in reads) {
            if (path !in captured) continue
            reporter.reportOn(read.source, LokkiDiagnostics.LOKKI_SLOT_READ_BEFORE_CAPTURE, function, hint, context)
        }
    }

    private fun notInterceptableReason(callee: FirCallableSymbol<*>, receiver: FirExpression): String? {
        val inline = if (callee is FirPropertySymbol) callee.getterSymbol?.isInline == true else callee.isInline
        val interception = with(context) { callee.objectInterception(receiver.resolvedType) }
        return when {
            callee is FirFieldSymbol -> "it is a field"
            inline -> "it is inline. $action the member it calls"
            callee.isClassFinalizer(session) -> FINALIZER_REASON
            interception is MemberInterception.NotIntercepted -> interception.reason
            interception == MemberInterception.Intercepted -> null
            callee.modality == Modality.FINAL && !callee.isOpenedInBytecode(session) -> "it is final"
            else -> receiver.resolvedType.fullyExpandedType(session).toRegularClassSymbol(session)
                ?.takeIf { it.isFinalType && !it.isOpenedInBytecode(session) }
                ?.let { "its receiver type '${it.displayName}' is final" }
        }
    }

    private fun checkArgument(argument: FirExpression, first: FirExpression?) {
        val expression = argument.unwrapArgument()
        when {
            expression is FirVarargArgumentsExpression -> expression.arguments.forEach { checkArgument(it, first) }
            expression.isContextValue() -> {}
            expression is FirQualifiedAccessExpression && expression.takesScope() -> {
                val matcher = expression.calleeReference.toResolvedCallableSymbol()
                    ?.takeIf { it.intrinsicKind(session) == IntrinsicKind.MATCHER }
                    ?.name?.asString()
                if (matcher == MatcherNames.ALL_ANY && expression !== first) {
                    report(expression, LokkiDiagnostics.LOKKI_ALL_ANY_NOT_FIRST)
                }
                val operands = expression.operands().filterNot { it.isScope() }
                val matchesOperands = matcher in MatcherNames.COMPOSITE || matcher == MatcherNames.ARRAY
                operands.forEach { if (matchesOperands) checkArgument(it, first) else misplacedMatchers(it) }
            }
            else -> misplacedMatchers(expression)
        }
    }

    private fun matcherInCallPosition(matcher: FirElement) {
        val operator = (matcher as? FirFunctionCall)?.shadowedInvoke()
            ?: return report(matcher, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION)
        reporter.reportOn(
            matcher.source,
            LokkiDiagnostics.LOKKI_INVOKE_MATCHER_SHADOWS_OPERATOR,
            operator.type.renderReadable(),
            operator.label,
            context,
        )
    }

    private fun FirFunctionCall.shadowedInvoke(): ImplicitReceiver? {
        val callee = calleeReference.toResolvedCallableSymbol() ?: return null
        if (callee.name != OperatorNameConventions.INVOKE || explicitReceiver != null) return null
        if (callee.intrinsicKind(session) != IntrinsicKind.MATCHER) return null
        return context.containingElements.asReversed().firstNotNullOfOrNull { element ->
            element.implicitReceiver()?.takeIf { it.type.hasOperatorInvoke() }
        }
    }

    private fun FirElement.implicitReceiver(): ImplicitReceiver? =
        when (this) {
            is FirAnonymousFunction -> receiverParameter?.typeRef?.coneType?.let { type ->
                label?.name?.let { ImplicitReceiver(type, it) }
            }
            is FirNamedFunction -> receiverParameter?.typeRef?.coneType?.let { ImplicitReceiver(it, name.asString()) }
            is FirRegularClass -> ImplicitReceiver(symbol.defaultType(), name.asString())
            else -> null
        }

    private fun ConeKotlinType.hasOperatorInvoke(): Boolean {
        val symbol = fullyExpandedType(session).toRegularClassSymbol(session) ?: return false
        var found = false
        with(context) {
            symbol.unsubstitutedScope().processFunctionsByName(OperatorNameConventions.INVOKE) {
                found = found || it.isOperator
            }
        }
        return found
    }

    private fun misplacedMatchers(expression: FirExpression) {
        expression.scopeCalls().forEach { report(it, LokkiDiagnostics.LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION) }
    }

    private fun FirElement.scopeCalls(): List<FirElement> {
        val calls = ArrayList<FirElement>()
        accept(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                when {
                    element.isConstructed() -> {}
                    element.takesScope() || element.isContextValue() -> calls += element
                    else -> element.acceptChildren(this)
                }
            }
        })
        return calls
    }

    private fun FirElement.takesScope(): Boolean =
        this is FirQualifiedAccessExpression && !isConstructed() && operands().any { it.isScope() }

    private fun FirElement.isConstructed(): Boolean =
        this is FirFunctionCall &&
            calleeReference.toResolvedCallableSymbol()?.intrinsicKind(session) == IntrinsicKind.CONSTRUCTED

    private fun FirQualifiedAccessExpression.operands(): List<FirExpression> =
        listOfNotNull(dispatchReceiver, extensionReceiver) + contextArguments + (this as? FirCall)?.arguments.orEmpty()

    private fun FirFunctionCall.isContextBlock(): Boolean =
        calleeReference.toResolvedCallableSymbol()?.intrinsicKind(session) == IntrinsicKind.WITH_CONTEXT &&
            extensionReceiver.isScope()

    private fun FirElement.isContextValue(): Boolean =
        this is FirPropertyAccessExpression && calleeReference.toResolvedCallableSymbol() in contexts

    private fun FirExpression?.isScope(): Boolean {
        val receiver = this?.unwrapArgument()?.unwrapSmartcastExpression() as? FirThisReceiverExpression
        return scope != null && receiver?.calleeReference?.boundSymbol == scope
    }

    private fun callExplicitly(member: String): String =
        if (mode == BlockMode.STUB) ". Call '$member' explicitly" else ""

    private fun Requirement.notSingleCall(element: FirElement, reason: String) {
        reporter.reportOn(
            element.source ?: block.source,
            LokkiDiagnostics.LOKKI_STUB_BLOCK_NOT_SINGLE_CALL,
            blockName,
            text,
            reason,
            context,
        )
    }

    private fun notInterceptable(call: FirQualifiedAccessExpression, callee: FirCallableSymbol<*>, reason: String) {
        val source = call.calleeReference.source ?: call.source ?: block.source
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_NOT_INTERCEPTABLE, callee.name.asString(), reason, context)
    }

    private fun report(element: FirElement, factory: KtDiagnosticFactory0) {
        reporter.reportOn(contextSources[element] ?: element.source ?: block.source, factory, context)
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
            else -> statement.wrapped ?: return statement
        }
    }
}

private fun FirExpression.links(): List<FirQualifiedAccessExpression> {
    val expression = unwrapArgument()
    expression.wrapped?.let { return it.links() }
    val receiver = (expression as? FirQualifiedAccessExpression)?.dispatchReceiver ?: return emptyList()
    return receiver.links() + expression
}

private val FirStatement.wrapped: FirExpression?
    get() = when {
        this is FirTypeOperatorCall && operation == FirOperation.AS -> argument
        this is FirCheckNotNullCall -> argument
        this is FirSafeCallExpression -> selector as? FirExpression
        this is FirCheckedSafeCallSubject -> originalReceiverRef.value
        this is FirSmartCastExpression -> originalExpression
        else -> null
    }

private fun FirExpression.slotPath(session: FirSession): List<FirBasedSymbol<*>>? =
    when (val expression = unwrapArgument().unwrapSmartcastExpression()) {
        is FirThisReceiverExpression -> expression.calleeReference.boundSymbol?.let(::listOf)
        is FirResolvedQualifier -> expression.accessedObject(session)?.let(::listOf)
        is FirPropertyAccessExpression -> {
            val symbol = expression.calleeReference.toResolvedCallableSymbol() ?: return null
            val receivers = listOfNotNull(expression.dispatchReceiver, expression.extensionReceiver)
            receivers.flatMap { it.slotPath(session) ?: return null } + symbol
        }
        else -> null
    }

private fun FirExpression.accessedObject(session: FirSession): FirRegularClassSymbol? =
    resolvedType.toRegularClassSymbol(session)?.takeIf { it.classKind == ClassKind.OBJECT }

private class ImplicitReceiver(val type: ConeKotlinType, val label: String)

private fun FirExpression.hasContextLambda(): Boolean =
    (unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction?.contextParameters?.isNotEmpty() == true

private val FirStatement.isLoop: Boolean
    get() = this is FirLoop || this is FirBlock && statements.lastOrNull() is FirLoop

private val FirStatement.isCompoundAssignment: Boolean
    get() = when (this) {
        is FirVariableAssignment -> source?.kind is KtFakeSourceElementKind.DesugaredAugmentedAssign
        is FirBlock -> statements.any { it is FirVariableAssignment }
        else -> false
    }

private const val SINGLE_CALL = "be a single call of a mock member"

private val STUB_SCOPES = setOf(CoreClass.STUB_SCOPE.id, CoreClass.ADDITIONAL_ANSWER_SCOPE.id)

private val COROUTINE_BUILDERS = setOf(
    FqName("kotlinx.coroutines.runBlocking"),
    FqName("kotlinx.coroutines.test.runTest"),
)

private val LIST_FACTORIES = setOf(FqName("kotlin.collections.listOf"), FqName("kotlin.collections.emptyList"))
