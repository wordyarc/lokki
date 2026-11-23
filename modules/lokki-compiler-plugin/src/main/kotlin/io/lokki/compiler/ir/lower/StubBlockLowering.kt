package io.lokki.compiler.ir.lower

import io.lokki.compiler.CHILD_MOCK_NEEDED
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.MatcherNames
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.VERIFICATION_ORDERINGS
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.NoCalls
import io.lokki.compiler.ir.NoConstructedCalls
import io.lokki.compiler.ir.ObjectRedirect
import io.lokki.compiler.ir.Redirect
import io.lokki.compiler.ir.StaticRedirect
import io.lokki.compiler.ir.StubbedCall
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.callsSuspend
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockProblem
import io.lokki.compiler.ir.collect.MockSites
import io.lokki.compiler.ir.collect.MockedTypes
import io.lokki.compiler.ir.contains
import io.lokki.compiler.ir.excludedCalls
import io.lokki.compiler.ir.generate.irFactory
import io.lokki.compiler.ir.generate.irRedirect
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayGet
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irCompanionCall
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.isLink
import io.lokki.compiler.ir.memberCall
import io.lokki.compiler.ir.memberKeys
import io.lokki.compiler.ir.rewrap
import io.lokki.compiler.ir.root
import io.lokki.compiler.ir.stubbedCall
import io.lokki.compiler.ir.toLambda
import io.lokki.compiler.ir.verifiedStatements
import io.lokki.compiler.ir.wrapped
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irIs
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSpreadElementImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class StubBlockLowering(
    lokki: LokkiIrContext,
    private val file: FileContext,
    private val sites: MockSites,
    private val mocks: Map<MockClassKey, IrClass>,
) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val builtIns = context.irBuiltIns
    private val matcherType = symbols[CoreClass.MATCHER].typeWith(builtIns.anyNType)
    private val matchersType = builtIns.arrayClass.typeWith(matcherType)

    fun lowerStub(every: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression =
        every.loweredOrIntact { stub(every, kind, scope) }

    fun lowerVerify(verify: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression =
        verify.loweredOrIntact { verify(verify, kind, scope) }

    fun lowerExclusion(exclude: IrCall, scope: Scope): IrExpression =
        exclude.loweredOrIntact { exclusion(exclude, scope) }

    fun lowerAwaits(just: IrCall, scope: Scope): IrExpression =
        just.loweredOrIntact { awaits(just, scope) }

    private inline fun IrCall.loweredOrIntact(lower: () -> IrExpression): IrExpression {
        val block = arguments.last() as? IrFunctionExpression
        if (block != null && block.contains { it is IrCall && it in file.failedDynamicCalls }) return this
        val intact = block?.function?.let { it.deepCopyWithSymbols(it.parent) }
        return try {
            lower()
        } catch (_: LoweringFailed) {
            if (intact != null) block.function = intact
            this
        }
    }

    private fun awaits(just: IrCall, scope: Scope): IrExpression {
        val awaitCancellation = awaitCancellation(just, "${just.name} Awaits")
        val answers = just.symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
        return builder(scope, just)
            .irAwaits(just, just.arguments[answers]!!, answers.type.erasedUpperBound, awaitCancellation)
            .patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun stub(every: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression {
        val block = every.stubBlock(stubs = true)
        val stubbed = block.function.stubbedCall()
            ?: fail(block.function, "The block of '${every.name}' must be a single call of a mock member")

        val justRuns = kind == IntrinsicKind.JUST_RUN_BLOCK
        val awaitCancellation = when (kind) {
            IntrinsicKind.JUST_AWAIT_BLOCK -> awaitCancellation(every, every.name)
            else -> null
        }
        val just = justRuns || awaitCancellation != null
        val answered = if (just) builtIns.unitType else every.typeArguments[0]!!
        val stubType = if (just) symbols[CoreClass.STUB_SCOPE].typeWith(answered) else every.type
        val registration = lambda(every, stubType)
        registration.body = builder(registration, every).irBlockBody {
            val call = irRegistration(block, stubbed)
            +irReturn(
                irInvoke(
                    symbols[if (every.isLenient) CoreFunction.STUB_LENIENT else CoreFunction.STUB],
                    irString(call.key),
                    call.matchers,
                    receiver = irGet(call.state),
                    type = stubType,
                    typeArguments = listOf(answered),
                )
            )
        }

        return builder(scope, every).run {
            val stub = irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], registration)
            when {
                awaitCancellation != null -> irAwaits(every, stub, symbols[CoreClass.STUB_SCOPE], awaitCancellation)
                justRuns -> irInvoke(symbols[CoreFunction.RETURNS], irUnit(), receiver = stub, type = every.type)
                else -> stub
            }
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun awaitCancellation(dsl: IrCall, name: String): IrSimpleFunction =
        symbols.awaitCancellation
            ?: fail(dsl, "'$name' needs kotlinx.coroutines.awaitCancellation(), which is not on the classpath")

    private fun IrBuilderWithScope.irAwaits(
        dsl: IrCall,
        answers: IrExpression,
        answersClass: IrClass,
        awaitCancellation: IrSimpleFunction,
    ): IrExpression {
        val type = (dsl.type as IrSimpleType).arguments.single().typeOrFail
        val answer = lambda(dsl, type, suspend = true)
        answer.addValueParameter("scope", symbols[CoreClass.ANSWER_SCOPE].typeWith(type))
        answer.addValueParameter("call", symbols[CoreClass.CALL].defaultType)
        answer.body = builder(answer, dsl).irBlockBody { +irReturn(irInvoke(awaitCancellation)) }
        val stubs = answersClass == symbols[CoreClass.STUB_SCOPE]
        val answerWith = symbols[if (stubs) CoreFunction.CO_ANSWERS else CoreFunction.CO_AND_THEN]
        return irInvoke(answerWith, answer.toLambda(builtIns), receiver = answers, type = dsl.type)
    }

    private fun verify(verify: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression {
        val unverified = verify.stubBlock()
        val expected = unverified.function.verifiedStatements()
            ?: fail(unverified.function, "The block of '${verify.name}' must consist of calls of mock members")
        expected.filterIsInstance<StubbedCall>().find { it.call.symbol.owner.isMethodOfAny() }?.let {
            val reason = "calls of equals, hashCode and toString cannot be verified"
            fail(it.call, "'${it.call.name}' is not recorded: $reason")
        }

        val check = lambda(verify, builtIns.unitType)
        return builder(scope, verify).irBlock {
            val verification = irTemporary(irVerification(verify, kind))
            val block = unverified.verifiedBy(verification)
            check.body = builder(check, verify).irBlockBody {
                for (expectation in expected) {
                    when (expectation) {
                        is NoCalls -> irExpectNoCalls(block, verification, expectation)
                        is NoConstructedCalls -> +irInvoke(
                            symbols[CoreFunction.EXPECT_NO_CONSTRUCTED_CALLS],
                            irRepresentative(expectation.call, block),
                            receiver = irGet(verification),
                        )
                        is StubbedCall -> {
                            val call = irRegistration(block, expectation)
                            +irInvoke(
                                symbols[CoreFunction.EXPECT],
                                irGet(call.state),
                                irString(call.key),
                                call.matchers,
                                receiver = irGet(verification),
                            )
                        }
                    }
                }
                +irInvoke(symbols[CoreFunction.CHECK], receiver = irGet(verification))
            }
            +irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], check)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun IrStatementsBuilder<*>.irExpectNoCalls(block: StubBlock, verification: IrVariable, expected: NoCalls) {
        val mocks = expected.mocks
        if (mocks is IrCall && mocks.intrinsicKind == IntrinsicKind.CONSTRUCTED) {
            +irInvoke(
                symbols.expectNoCalls(builtIns.anyClass.owner),
                irRepresentative(mocks, block),
                receiver = irGet(verification),
            )
            return
        }
        val pattern = mocks.memberCall()?.takeIf {
            it.call.symbol.owner.isLink || file.redirects.member(it.call.symbol.owner) != null
        }
        if (pattern == null || pattern.callsSuspend) {
            if (!mocks.isIndependentOf(block.function)) {
                fail(mocks, "The receiver of 'wasNot Called' must not use matchers")
            }
            val evaluated = if (mocks.callsSuspend()) irEvaluate(block, mocks.type) { mocks } else mocks
            +irInvoke(symbols.expectNoCalls(expected.mocksClass), evaluated, receiver = irGet(verification))
            return
        }
        val unchained = block.withoutExpectations()
        val receiver = pattern.receiver?.let { irTemporary(irLinks(unchained, it)) }
        val independent = pattern.arguments.all { it == null || it.isIndependentOf(block.function) }
        val mayBeValue = receiver != null && independent && file.redirects.member(pattern.call.symbol.owner) == null
        val value = if (mayBeValue) pattern.call.deepCopyWithSymbols() else null
        val call = StubbedCall(pattern.call, receiver?.let(::irGet), pattern.arguments, pattern.order)
        val expectAbsent = irBlock {
            val registration = irRegistration(unchained, call)
            +irInvoke(
                symbols[CoreFunction.EXPECT_ABSENT],
                irGet(registration.state),
                irString(registration.key),
                registration.matchers,
                receiver = irGet(verification),
            )
        }
        if (value == null) {
            +expectAbsent
            return
        }
        value.dispatchReceiver = irGet(receiver!!)
        value.symbol.owner.nonDispatchParameters.forEachIndexed { index, parameter ->
            value.arguments[parameter] = pattern.arguments[index]?.deepCopyWithSymbols()
        }
        val expectValue = irInvoke(symbols.expectNoCalls(expected.mocksClass), value, receiver = irGet(verification))
        +irIfThenElse(
            builtIns.unitType,
            irIs(irGet(receiver), symbols[CoreClass.GENERATED_MOCK].defaultType),
            expectAbsent,
            expectValue,
        )
    }

    private val IrCall.isLenient: Boolean
        get() = symbol.owner.parentClassOrNull?.symbol == symbols[CoreClass.LENIENT_STUBBING_SCOPE].symbol

    private fun exclusion(exclude: IrCall, scope: Scope): IrExpression {
        val block = exclude.stubBlock()
        val excluded = block.function.excludedCalls()
            ?: fail(block.function, "The block of '${exclude.name}' must consist of calls of mock members")

        val registration = lambda(exclude, builtIns.unitType)
        return builder(scope, exclude).irBlock {
            val current = exclude.arguments[0]?.let { irTemporary(it) }
            registration.body = builder(registration, exclude).irBlockBody {
                for (stubbed in excluded) {
                    val call = irRegistration(block, stubbed)
                    +irInvoke(
                        symbols[CoreFunction.EXCLUDE],
                        irString(call.key),
                        call.matchers,
                        current?.let(::irGet),
                        receiver = irGet(call.state),
                    )
                }
            }
            +irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], registration)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun IrBuilder.irVerification(verify: IrCall, kind: IntrinsicKind): IrExpression {
        val function = verify.symbol.owner
        val calls = function.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
        if (calls != null) return irInvoke(symbols.verificationOf(calls.type.erasedUpperBound), verify.arguments[calls])
        val settings = function.parameters.filter { it.kind == IrParameterKind.Regular }.dropLast(1)
        return irCallConstructor(symbols[CoreConstructor.VERIFICATION].symbol, emptyList()).apply {
            VERIFICATION_ORDERINGS[kind]?.let(symbols::orderingEntry)?.let { ordering ->
                val type = symbols[CoreClass.ORDERING].defaultType
                arguments[symbols.verificationParameter(ParameterNames.ORDERING)] =
                    IrGetEnumValueImpl(startOffset, endOffset, type, ordering.symbol)
            }
            for (setting in settings) {
                arguments[symbols.verificationParameter(setting.name.asString())] = verify.arguments[setting]
            }
        }
    }

    private fun IrCall.stubBlock(stubs: Boolean = false): StubBlock {
        val block = (arguments.last() as? IrFunctionExpression)?.function
            ?: fail(this, "The block of '$name' must be a lambda literal")
        return StubBlock(this, block, stubs)
    }

    private fun IrStatementsBuilder<*>.irRegistration(block: StubBlock, stubbed: StubbedCall): Registration {
        val target = targetOf(stubbed.call)
        if (!stubbed.callsSuspend) return irEvaluatedRegistration(block, stubbed, target)
        val anyN = builtIns.anyNType
        val evaluated = irTemporary(
            irEvaluate(block, builtIns.arrayClass.typeWith(anyN)) { nested ->
                val registration = irEvaluatedRegistration(nested, stubbed, target)
                irArrayOf(anyN, listOf(irGet(registration.state), registration.matchers))
            }
        )
        return Registration(
            irTemporary(irAs(irArrayGet(symbols, evaluated, 0), symbols[CoreClass.MOCK_STATE].defaultType)),
            target.key,
            irAs(irArrayGet(symbols, evaluated, 1), matchersType),
        )
    }

    private fun IrStatementsBuilder<*>.irEvaluatedRegistration(
        block: StubBlock,
        stubbed: StubbedCall,
        target: Target,
    ): Registration {
        val receiver = stubbed.receiver?.let { irTemporary(irLinks(block, it)) }
        val state = irTemporary(
            irStateOf(block, target.redirect, receiver?.let(::irGet)) ?: fail(
                stubbed.call,
                "'${stubbed.call.name}' has no interception point: lokki intercepts top-level functions and " +
                    "properties of main compilations with lokki { mockable(...) }",
            )
        )
        val matchers = irArguments(block, stubbed, receiver, state, target.key)
        return Registration(state, target.key, matchers)
    }

    private fun IrStatementsBuilder<*>.irArguments(
        block: StubBlock,
        stubbed: StubbedCall,
        receiver: IrVariable?,
        state: IrVariable,
        key: String,
    ): IrExpression {
        val call = stubbed.call
        val member = call.symbol.owner
        val parameters = member.nonDispatchParameters
        val declared = member.root.parameters
        val capturesDefaults = !stubbed.allAny && null in stubbed.arguments
        val values = arrayOfNulls<IrVariable>(parameters.size)
        val matchers = arrayOfNulls<IrVariable>(parameters.size)
        for (index in stubbed.order) {
            val argument = stubbed.arguments[index] ?: continue
            val parameter = parameters[index]
            if (argument.isIndependentOf(block.function)) {
                values[index] = irTemporary(argument)
                continue
            }
            if (capturesDefaults) placeholderProblem(member, parameter, argument)?.let { fail(argument, it) }
            val declaredParameter = declared[parameter.indexInParameters]
            matchers[index] = irTemporary(irArgumentMatcher(argument, declaredParameter, block, stubbed.allAny))
        }
        val defaults = if (capturesDefaults) {
            if (receiver != null) call.dispatchReceiver = irGet(receiver)
            irTemporary(irCaptureDefaults(block.dsl, state, key, stubbed, values))
        } else {
            null
        }
        val arguments = parameters.indices.map { index ->
            when {
                matchers[index] != null -> irGet(matchers[index]!!)
                stubbed.allAny -> irAny()
                else -> irEq(values[index]?.let(::irGet) ?: irArrayGet(symbols, defaults!!, index))
            }
        }
        return irArrayOf(matcherType, arguments)
    }

    private fun IrStatementsBuilder<*>.irLinks(block: StubBlock, receiver: IrExpression): IrExpression {
        val verification = block.verification
        if (receiver is IrCall && receiver.intrinsicKind == IntrinsicKind.CONSTRUCTED) {
            return irRepresentative(receiver, block)
        }
        receiver.wrapped?.let { wrapped ->
            receiver.rewrap(irLinks(block, wrapped))
            return receiver
        }
        val link = receiver.memberCall()?.takeIf { it.call.symbol.owner.isLink }
        if (link == null) {
            if (receiver.isIndependentOf(block.function)) return receiver
            fail(receiver, "A matcher in the receiver of the stubbed or verified call must be an argument of a link of the call chain")
        }
        val call = link.call
        val parameters = call.symbol.owner.nonDispatchParameters
        val head = irTemporary(irLinks(block, link.receiver!!))
        val key = call.symbol.owner.memberKeys.first()
        if (link.arguments.any { it?.isIndependentOf(block.function) == false }) {
            return irMatchedLink(block, link, head, key)
        }
        val values = arrayOfNulls<IrVariable>(parameters.size)
        for (index in link.order) values[index] = link.arguments[index]?.let { irTemporary(it) }
        call.dispatchReceiver = irGet(head)
        parameters.forEachIndexed { index, parameter -> call.arguments[parameter] = values[index]?.let(::irGet) }
        if (verification != null) {
            +irInvoke(
                symbols[CoreFunction.EXPECT_LINK],
                irGet(head),
                irString(key),
                irLinkMatchers(block.dsl, link, key, values),
                receiver = irGet(verification),
            )
        }
        return irLink(block, head, key, call)
    }

    private fun IrStatementsBuilder<*>.irRepresentative(call: IrCall, block: StubBlock): IrExpression {
        val type = call.typeArguments[0]!!
        val target = type.classOrNull?.owner ?: fail(call, "'${call.name}' needs a class as its type argument")
        val redirect = file.redirects.constructors(target)
            ?: fail(call, "'${target.name}' has no constructor interception point: add it to lokki { mockable(...) }")
        val key = irString(redirect.key)
        val name = irString(target.name.asString())
        val parameter = call.symbol.owner.parameters.find { it.varargElementType != null }
        val elements = parameter?.let { call.arguments[it] as? IrVararg }?.elements.orEmpty()
        val representative = if (elements.isEmpty()) {
            irInvoke(symbols[CoreFunction.CONSTRUCTED], key, name)
        } else {
            val matchers = elements.map { element ->
                val argument = element as? IrExpression
                    ?: fail(call, "'${call.name}' takes its arguments one by one, not as a spread array")
                irMatcher(argument, builtIns.anyClass.owner, block)
            }
            irInvoke(symbols[CoreFunction.CONSTRUCTED_WITH], key, name, irArrayOf(matcherType, matchers))
        }
        return irAs(representative, type)
    }

    private fun IrBuilderWithScope.irLink(block: StubBlock, head: IrVariable, key: String, call: IrCall): IrExpression {
        val type = call.type
        val child = childOf(call)
        val evaluation = lambda(block.dsl, type)
        evaluation.body = builder(evaluation, block.dsl).irBlockBody { +irReturn(call) }
        return irInvoke(
            symbols[CoreFunction.LINK],
            irGet(head),
            irString(key),
            irBoolean(block.stubs),
            child?.let { (mock, types) -> irFactory(symbols, mock, types) } ?: irNull(),
            if (child == null) irString(childProblem(type)) else irNull(),
            evaluation.toLambda(builtIns),
            type = type,
            typeArguments = listOf(type),
        )
    }

    private fun IrStatementsBuilder<*>.irMatchedLink(
        block: StubBlock,
        link: StubbedCall,
        head: IrVariable,
        key: String,
    ): IrExpression {
        val type = link.call.type
        val (mock, types) = childOf(link.call) ?: when (val problem = file.mockability.childProblem(type)) {
            null -> fail(link.call, childProblem(type))
            else -> fail(link.call, problem.withReason("${problem.reason}. $CHILD_MOCK_NEEDED"))
        }
        val state = irTemporary(irStateOf(irGet(head)))
        val matchers = irTemporary(irArguments(block, link, head, state, targetOf(link.call).key))
        block.verification?.let { verification ->
            +irInvoke(
                symbols[CoreFunction.EXPECT_LINK],
                irGet(head),
                irString(key),
                irMatchersOf(block.dsl, matchers),
                receiver = irGet(verification),
            )
        }
        val child = irInvoke(
            symbols[CoreFunction.MOCK_STATE_LINK],
            irString(key),
            irGet(matchers),
            irBoolean(block.stubs),
            irFactory(symbols, mock, types),
            receiver = irGet(state),
        )
        return irAs(child, type)
    }

    private fun childOf(link: IrCall): Pair<IrClass, MockedTypes>? {
        val site = sites.links[link] ?: return null
        return mocks[site.key]?.let { it to site.types }
    }

    private fun childProblem(type: IrType): String =
        file.mockability.childProblem(type)?.message ?: "lokki generated no child mock class for '${type.render()}'"

    private fun IrBuilder.irMatchersOf(dsl: IrCall, matchers: IrVariable): IrExpression {
        val function = lambda(dsl, matchersType)
        function.addValueParameter("state", symbols[CoreClass.MOCK_STATE].defaultType)
        function.body = builder(function, dsl).irBlockBody { +irReturn(irGet(matchers)) }
        return function.toLambda(builtIns)
    }

    private fun IrBuilder.irLinkMatchers(
        dsl: IrCall,
        link: StubbedCall,
        key: String,
        values: Array<IrVariable?>,
    ): IrExpression {
        val matchers = lambda(dsl, matchersType)
        val state = matchers.addValueParameter("state", symbols[CoreClass.MOCK_STATE].defaultType)
        matchers.body = builder(matchers, dsl).irBlockBody {
            val defaults = if (null in link.arguments) {
                val capture = StubbedCall(link.call.deepCopyWithSymbols(), link.receiver, link.arguments, link.order)
                irTemporary(irCaptureDefaults(dsl, state, key, capture, values))
            } else {
                null
            }
            val arguments = values.indices.map { index ->
                irEq(values[index]?.let(::irGet) ?: irArrayGet(symbols, defaults!!, index))
            }
            +irReturn(irArrayOf(matcherType, arguments))
        }
        return matchers.toLambda(builtIns)
    }

    private inline fun IrBuilderWithScope.irEvaluate(
        block: StubBlock,
        type: IrType,
        value: IrBlockBodyBuilder.(StubBlock) -> IrExpression,
    ): IrExpression {
        val evaluation = lambda(block.dsl, type, suspend = true)
        val nested = block.nested()
        evaluation.body = builder(evaluation, block.dsl).irBlockBody { +irReturn(value(nested)) }
        return irInvoke(
            symbols[CoreFunction.EVALUATE],
            irString(block.dsl.name),
            evaluation.toLambda(builtIns),
            type = type,
            typeArguments = listOf(type),
        )
    }

    private fun IrBuilder.irCaptureDefaults(
        dsl: IrCall,
        state: IrValueDeclaration,
        key: String,
        stubbed: StubbedCall,
        values: Array<IrVariable?>,
    ): IrExpression {
        val call = stubbed.call
        val member = call.symbol.owner
        val parameters = member.nonDispatchParameters
        val capture = lambda(dsl, builtIns.unitType, suspend = member.isSuspend)
        val alternate = capture.addValueParameter("alternate", builtIns.booleanType)
        parameters.forEachIndexed { index, parameter ->
            call.arguments[parameter] = stubbed.arguments[index]?.let { argument ->
                values[index]?.let(::irGet) ?: irPlaceholder(argument, alternate)
            }
        }
        capture.body = builder(capture, dsl).irBlockBody { +call }
        val captureDefaults = if (member.isSuspend) CoreFunction.CO_CAPTURE_DEFAULTS else CoreFunction.CAPTURE_DEFAULTS
        val omitted = parameters.mapIndexed { index, parameter ->
            if (stubbed.arguments[index] == null) irString(parameter.name.asString()) else irNull()
        }
        return irInvoke(
            symbols[captureDefaults],
            irString(key),
            irArrayOf(builtIns.stringType.makeNullable(), omitted),
            capture.toLambda(builtIns),
            receiver = irGet(state),
        )
    }

    private fun fail(element: IrElement, reason: String): Nothing {
        file.diagnostics.unsupported(element, reason)
        throw LoweringFailed()
    }

    private fun fail(element: IrElement, problem: MockProblem): Nothing {
        file.diagnostics.report(element, problem)
        throw LoweringFailed()
    }

    private fun lambda(dsl: IrCall, type: IrType, suspend: Boolean = false): IrSimpleFunction =
        context.buildLambda(dsl.startOffset, dsl.endOffset, type, suspend)

    private fun builder(function: IrSimpleFunction, dsl: IrCall): DeclarationIrBuilder =
        DeclarationIrBuilder(context, function.symbol, dsl.startOffset, dsl.endOffset)

    private fun builder(scope: Scope, dsl: IrCall): DeclarationIrBuilder =
        DeclarationIrBuilder(context, scope.scopeOwnerSymbol, dsl.startOffset, dsl.endOffset)

    private fun placeholderProblem(
        member: IrSimpleFunction,
        parameter: IrValueParameter,
        argument: IrExpression,
    ): String? =
        when {
            argument.type.classOrNull?.owner?.isValue == true ->
                "A matcher of a value class cannot be combined with omitted arguments: pass all arguments explicitly"
            parameter.kind == IrParameterKind.ExtensionReceiver && !member.isSuspend &&
                !parameter.type.isNullable() && !argument.type.isPrimitiveType() ->
                "A matcher of the extension receiver cannot be combined with omitted arguments: " +
                    "pass all arguments explicitly"
            else -> null
        }

    private fun IrStatementsBuilder<*>.irArgumentMatcher(
        argument: IrExpression,
        declared: IrValueParameter,
        block: StubBlock,
        allAny: Boolean,
    ): IrExpression {
        val arrayType = declared.type.erasedUpperBound
        if (argument !is IrVararg) return irMatcher(argument, arrayType, block)
        return irElementsMatcher(argument, declared.varargElementType!!.erasedUpperBound, arrayType, block, allAny)
    }

    private fun IrStatementsBuilder<*>.irElementsMatcher(
        argument: IrVararg?,
        elementType: IrClass,
        arrayType: IrClass,
        block: StubBlock,
        allAny: Boolean,
    ): IrExpression {
        val parts = argument?.elements.orEmpty()
        val spreadMatchers = parts.filterIsInstance<IrSpreadElement>()
            .filterNot { it.expression.isIndependentOf(block.function) }
        if (spreadMatchers.size > 1) {
            fail(spreadMatchers[1].expression, "A vararg argument cannot contain more than one vararg matcher")
        }
        val spreadMatcher = spreadMatchers.singleOrNull()
        val elements = parts.map { element ->
            when {
                element !is IrSpreadElement -> irMatcher(element as IrExpression, elementType, block, allAny)
                element === spreadMatcher -> irSpreadMatcher(element.expression, arrayType, block)
                else -> IrSpreadElementImpl(
                    startOffset,
                    endOffset,
                    irInvoke(symbols.matcherFactory(MatcherNames.SPREAD_FACTORY), element.expression),
                )
            }
        }
        if (spreadMatcher == null) {
            return irInvoke(symbols[CoreConstructor.ARRAY_MATCHER], irArrayOf(matcherType, elements))
        }
        val position = parts.indexOf(spreadMatcher)
        return irInvoke(
            symbols.matcherFactory(MatcherNames.VARARG_OF_FACTORY),
            irArrayOf(matcherType, elements.subList(0, position)),
            elements[position] as IrExpression,
            irArrayOf(matcherType, elements.subList(position + 1, elements.size)),
        )
    }

    private fun IrStatementsBuilder<*>.irSpreadMatcher(
        matcher: IrExpression,
        arrayType: IrClass,
        block: StubBlock,
    ): IrExpression {
        if (matcher.isBuiltinMatcher && (matcher as IrCall).name !in MatcherNames.VARARG) {
            fail(
                matcher,
                "The matcher '${matcher.name}' cannot be spread into a vararg parameter: " +
                    "only 'anyVararg', 'varargAll' and 'varargAny' match a part of the array",
            )
        }
        return irMatcher(matcher, arrayType, block)
    }

    private fun IrStatementsBuilder<*>.irMatcher(
        argument: IrExpression,
        declared: IrClass,
        block: StubBlock,
        allAny: Boolean = false,
    ): IrExpression =
        when {
            argument.isIndependentOf(block.function) -> if (allAny) irAny() else irEq(argument)
            argument.isBuiltinMatcher -> irBuiltinMatcher(argument as IrCall, declared, block)
            argument.isHelperOf(block.function) -> irHelperMatcher(argument, block)
            argument.contains { it.returnsFrom(block.function) } ->
                fail(argument, "An argument of the stubbed or verified call must not return from the block")
            else -> fail(
                argument,
                "A matcher inside an expression is not supported: pass the matcher as a whole argument",
            )
        }

    private fun IrStatementsBuilder<*>.irBuiltinMatcher(call: IrCall, declared: IrClass, block: StubBlock): IrExpression {
        val name = call.name
        val classified = name in MatcherNames.CLASSIFIED && call.symbol.owner.nonDispatchParameters.any {
            it.type.classOrNull == builtIns.kClassClass
        }
        val matcher = when {
            classified -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(block.function) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
            else -> irBuiltinMatcher(name, call, declared, block)
        }
        if (classified) return matcher
        val type = call.typeArguments.firstOrNull() ?: return matcher
        if (name !in MatcherNames.TYPED || !type.isStrictSubtypeOf(declared)) return matcher
        return irInvoke(symbols.matcherFactory(MatcherNames.TYPED_FACTORY), matcher, typeArguments = listOf(type))
    }

    private fun IrStatementsBuilder<*>.irBuiltinMatcher(
        name: String,
        call: IrCall,
        declared: IrClass,
        block: StubBlock,
    ): IrExpression =
        when (name) {
            in MatcherNames.ANY -> irAny()
            in MatcherNames.COMPOSITE -> irFactory(call) { irMatcher(it, declared, block) }
            MatcherNames.ARRAY -> {
                val elements = call.symbol.owner.parameters.single { it.varargElementType != null }
                val argument = call.arguments[elements] as IrVararg?
                irElementsMatcher(argument, builtIns.anyClass.owner, declared, block, allAny = false)
            }
            else -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(block.function) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
        }

    private inline fun IrBuilder.irFactory(call: IrCall, argument: (IrExpression) -> IrExpression): IrExpression {
        val function = call.symbol.owner
        val factory = symbols.matcherFactory(function)
        val arguments = function.nonDispatchParameters.map { parameter -> call.arguments[parameter]?.let(argument) }
        return irInvoke(factory, *arguments.toTypedArray()).apply {
            if (factory.typeParameters.isNotEmpty()) typeArguments[0] = call.typeArguments[0]
        }
    }

    private fun IrStatementsBuilder<*>.irHelperMatcher(helper: IrExpression, block: StubBlock): IrExpression {
        val scope = block.scope
            ?: irTemporary(irInvoke(symbols[CoreConstructor.REGISTRATION_SCOPE]), irType = block.receiverType)
                .also { block.scope = it }
        val value = helper.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression =
                    if (expression.isScopeOf(block.function)) irGet(scope) else expression
            },
            null,
        )
        return irInvoke(symbols[CoreFunction.MATCHER_OF], value, receiver = irGet(scope))
    }

    private fun IrBuilder.irAny(): IrExpression = irGetObject(symbols[CoreClass.ANY_MATCHER])

    private fun targetOf(call: IrCall): Target {
        val member = call.symbol.owner
        val redirected = file.redirects.member(member)
        return Target(redirected?.redirect, redirected?.key ?: member.memberKeys.first())
    }

    private fun IrBuilderWithScope.irStateOf(
        block: StubBlock,
        redirect: Redirect?,
        receiver: IrExpression?,
    ): IrExpression? {
        if (redirect == null) return receiver?.let { irStateOf(it) }
        val name = irString(redirect.name)
        val mock = mocks[redirect.site.key]?.takeIf { block.stubs }
        val target = when (redirect) {
            is ObjectRedirect -> receiver!!
            is StaticRedirect -> irString(redirect.key)
        }
        val redirected = when {
            mock == null && redirect is ObjectRedirect -> irInvoke(symbols[CoreFunction.REDIRECT_OF], target, name)
            mock == null -> irInvoke(symbols[CoreFunction.STATIC_REDIRECT_OF], target, name)
            redirect is ObjectRedirect -> irInvoke(symbols[CoreFunction.STUBBED_REDIRECT], irRedirect(symbols, mock, target, redirect))
            else -> irInvoke(symbols[CoreFunction.STUBBED_STATIC_REDIRECT], irRedirect(symbols, mock, target, redirect))
        }
        return irInvoke(symbols.state.getter!!, receiver = redirected)
    }

    private fun IrBuilder.irStateOf(instance: IrExpression): IrExpression =
        irInvoke(symbols.state.getter!!, receiver = irInvoke(symbols[CoreFunction.OF], instance))

    private fun IrBuilder.irEq(value: IrExpression): IrExpression = irInvoke(symbols[CoreConstructor.EQ_MATCHER], value)

    private fun IrBuilder.irPlaceholder(argument: IrExpression, alternate: IrValueParameter): IrExpression {
        val type = argument.type
        return when {
            argument is IrVararg -> IrVarargImpl(startOffset, endOffset, type, argument.varargElementType)
            type.isBoolean() -> irGet(alternate)
            type.isPrimitiveType() -> irIfThenElse(
                type,
                irGet(alternate),
                irOne(type),
                IrConstImpl.defaultValueForType(startOffset, endOffset, type),
            )
            else -> irNull(type)
        }
    }

    private fun IrBuilder.irOne(type: IrType): IrExpression =
        when {
            type.isByte() -> IrConstImpl.byte(startOffset, endOffset, type, 1)
            type.isShort() -> IrConstImpl.short(startOffset, endOffset, type, 1)
            type.isLong() -> IrConstImpl.long(startOffset, endOffset, type, 1)
            type.isFloat() -> IrConstImpl.float(startOffset, endOffset, type, 1f)
            type.isDouble() -> IrConstImpl.double(startOffset, endOffset, type, 1.0)
            type.isChar() -> IrConstImpl.char(startOffset, endOffset, type, '\u0001')
            else -> IrConstImpl.int(startOffset, endOffset, type, 1)
        }
}

private class StubBlock(
    val dsl: IrCall,
    val function: IrSimpleFunction,
    val stubs: Boolean,
    val verification: IrVariable? = null,
) {
    var scope: IrVariable? = null

    val receiverType: IrType
        get() = function.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }.type

    fun nested(): StubBlock = StubBlock(dsl, function, stubs, verification)

    fun verifiedBy(verification: IrVariable): StubBlock = StubBlock(dsl, function, stubs, verification)

    fun withoutExpectations(): StubBlock = StubBlock(dsl, function, stubs).also { it.scope = scope }
}

private class Registration(val state: IrVariable, val key: String, val matchers: IrExpression)

private class Target(val redirect: Redirect?, val key: String)

private class LoweringFailed : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

private val IrCall.name: String
    get() = symbol.owner.name.asString()

private val IrExpression.isBuiltinMatcher: Boolean
    get() = this is IrCall && intrinsicKind == IntrinsicKind.MATCHER && name in MatcherNames.BUILTIN

private fun IrType.isStrictSubtypeOf(declared: IrClass): Boolean {
    if (classifierOrNull is IrTypeParameterSymbol) return true
    val type = erasedUpperBound
    return type != declared && type.isSubclassOf(declared)
}

private fun IrElement.isScopeOf(block: IrSimpleFunction): Boolean =
    this is IrGetValue && (symbol.owner as? IrValueParameter)?.parent == block

private fun IrElement.returnsFrom(block: IrSimpleFunction): Boolean =
    this is IrReturn && returnTargetSymbol == block.symbol

private fun IrExpression.isIndependentOf(block: IrSimpleFunction): Boolean =
    !contains { it.isScopeOf(block) || it.returnsFrom(block) }

private fun IrExpression.isHelperOf(block: IrSimpleFunction): Boolean =
    this is IrCall && arguments.any { it?.isScopeOf(block) == true } && !contains { it.returnsFrom(block) }
