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
import io.lokki.compiler.ir.ObjectRedirect
import io.lokki.compiler.ir.Redirect
import io.lokki.compiler.ir.StaticRedirect
import io.lokki.compiler.ir.StubbedCall
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.callsSuspend
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockedTypes
import io.lokki.compiler.ir.contains
import io.lokki.compiler.ir.excludedCalls
import io.lokki.compiler.ir.generate.irFactory
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irCompanionCall
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.isChain
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
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
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
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class StubBlockLowering(
    lokki: LokkiIrContext,
    private val file: FileContext,
    private val mocks: Map<MockClassKey, IrClass>,
) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val builtIns = context.irBuiltIns
    private val chainedMocks = file.chainedMocks
    private val matcherType = symbols[CoreClass.MATCHER].typeWith(builtIns.anyNType)
    private val matchersType = builtIns.arrayClass.typeWith(matcherType)

    fun lowerStub(every: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression =
        every.loweredOrIntact { stub(every, kind, scope) }

    fun lowerVerify(verify: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression =
        verify.loweredOrIntact { verify(verify, kind, scope) }

    fun lowerExclusion(exclude: IrCall, scope: Scope): IrExpression =
        exclude.loweredOrIntact { exclusion(exclude, scope) }

    private inline fun IrCall.loweredOrIntact(lower: () -> IrExpression?): IrExpression {
        val block = arguments.last() as? IrFunctionExpression
        val intact = block?.function?.let { it.deepCopyWithSymbols(it.parent) }
        return lower() ?: also { if (intact != null) block.function = intact }
    }

    fun lowerAwaits(just: IrCall, scope: Scope): IrExpression {
        val awaitCancellation = awaitCancellation(just, "${just.name} Awaits") ?: return just
        val answers = just.symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
        val answersClass = answers.type.erasedUpperBound
        return builder(scope, just)
            .irAwaits(just, just.arguments[answers.indexInParameters]!!, answersClass, awaitCancellation)
            .patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun stub(every: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression? {
        val block = every.stubBlock(stubs = true) ?: return null
        val stubbed = block.function.stubbedCall()
            ?: return fail(block.function, "The block of '${every.name}' must be a single call of a mock member")

        val justRuns = kind == IntrinsicKind.JUST_RUN_BLOCK
        val awaitCancellation = when (kind) {
            IntrinsicKind.JUST_AWAIT_BLOCK -> awaitCancellation(every, every.name) ?: return null
            else -> null
        }
        val just = justRuns || awaitCancellation != null
        val answered = if (just) builtIns.unitType else every.typeArguments[0]!!
        val stubType = if (just) symbols[CoreClass.STUB_SCOPE].typeWith(answered) else every.type
        val registration = lambda(every, stubType)
        registration.body = builder(registration, every).irBlockBody {
            val call = irRegistration(block, stubbed) ?: return null
            +irReturn(
                irCall(symbols[CoreFunction.STUB].symbol, stubType, listOf(answered)).apply {
                    arguments[0] = irGet(call.state)
                    arguments[1] = irString(call.key)
                    arguments[2] = call.matchers
                }
            )
        }

        return builder(scope, every).run {
            val stub = irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], registration)
            when {
                awaitCancellation != null -> irAwaits(every, stub, symbols[CoreClass.STUB_SCOPE], awaitCancellation)
                justRuns -> irCall(symbols[CoreFunction.RETURNS].symbol, every.type).apply {
                    arguments[0] = stub
                    arguments[1] = irUnit()
                }
                else -> stub
            }
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun awaitCancellation(dsl: IrCall, name: String): IrSimpleFunction? =
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
        answer.body = builder(answer, dsl).irBlockBody { +irReturn(irCall(awaitCancellation)) }
        val stubs = answersClass == symbols[CoreClass.STUB_SCOPE]
        val answerWith = symbols[if (stubs) CoreFunction.CO_ANSWERS else CoreFunction.CO_AND_THEN]
        return irCall(answerWith.symbol, dsl.type).apply {
            arguments[0] = answers
            arguments[1] = answer.toLambda(builtIns)
        }
    }

    private fun verify(verify: IrCall, kind: IntrinsicKind, scope: Scope): IrExpression? {
        val unverified = verify.stubBlock() ?: return null
        val expected = unverified.function.verifiedStatements()
            ?: return fail(unverified.function, "The block of '${verify.name}' must consist of calls of mock members")
        expected.filterIsInstance<StubbedCall>().find { it.call.symbol.owner.isMethodOfAny() }?.let {
            val reason = "calls of equals, hashCode and toString cannot be verified"
            return fail(it.call, "'${it.call.name}' is not recorded: $reason")
        }

        val check = lambda(verify, builtIns.unitType)
        return builder(scope, verify).irBlock {
            val verification = irTemporary(irVerification(verify, kind))
            val block = unverified.verifiedBy(verification)
            check.body = builder(check, verify).irBlockBody {
                for (expectation in expected) {
                    when (expectation) {
                        is NoCalls -> {
                            val mocks = expectation.mocks
                            if (!mocks.isIndependentOf(block.function)) {
                                return fail(mocks, "The receiver of 'wasNot Called' must not use matchers")
                            }
                            val evaluated = when {
                                mocks.callsSuspend() -> irEvaluate(block, mocks.type) { mocks }
                                else -> mocks
                            }
                            +irCall(symbols.expectNoCalls(expectation.mocksClass)).apply {
                                arguments[0] = irGet(verification)
                                arguments[1] = evaluated
                            }
                        }
                        is StubbedCall -> {
                            val call = irRegistration(block, expectation) ?: return null
                            +irCall(symbols[CoreFunction.EXPECT]).apply {
                                arguments[0] = irGet(verification)
                                arguments[1] = irGet(call.state)
                                arguments[2] = irString(call.key)
                                arguments[3] = call.matchers
                            }
                        }
                    }
                }
                +irCall(symbols[CoreFunction.CHECK]).apply { arguments[0] = irGet(verification) }
            }
            +irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], check)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun exclusion(exclude: IrCall, scope: Scope): IrExpression? {
        val block = exclude.stubBlock() ?: return null
        val excluded = block.function.excludedCalls()
            ?: return fail(block.function, "The block of '${exclude.name}' must consist of calls of mock members")

        val registration = lambda(exclude, builtIns.unitType)
        return builder(scope, exclude).irBlock {
            val current = exclude.arguments[0]?.let { irTemporary(it) }
            registration.body = builder(registration, exclude).irBlockBody {
                for (stubbed in excluded) {
                    val call = irRegistration(block, stubbed) ?: return null
                    +irCall(symbols[CoreFunction.EXCLUDE]).apply {
                        arguments[0] = irGet(call.state)
                        arguments[1] = irString(call.key)
                        arguments[2] = call.matchers
                        arguments[3] = current?.let(::irGet)
                    }
                }
            }
            +irCompanionCall(symbols[CoreFunction.WITHOUT_STATE_CHANGES], registration)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun IrBuilder.irVerification(verify: IrCall, kind: IntrinsicKind): IrExpression {
        val function = verify.symbol.owner
        val calls = function.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
        if (calls != null) {
            return irCallConstructor(symbols.verificationOf(calls.type.erasedUpperBound).symbol, emptyList()).apply {
                arguments[0] = verify.arguments[calls.indexInParameters]
            }
        }
        val constructor = symbols[CoreConstructor.VERIFICATION]
        val ordering = VERIFICATION_ORDERINGS[kind]?.let(symbols::orderingEntry)
        return irCallConstructor(constructor.symbol, emptyList()).apply {
            for (parameter in constructor.parameters) {
                val argument = function.parameters.find { it.name == parameter.name }
                arguments[parameter.indexInParameters] = when {
                    argument != null -> verify.arguments[argument.indexInParameters]
                    ordering != null && parameter.name.asString() == ParameterNames.ORDERING -> {
                        val type = symbols[CoreClass.ORDERING].defaultType
                        IrGetEnumValueImpl(startOffset, endOffset, type, ordering.symbol)
                    }
                    else -> continue
                }
            }
        }
    }

    private fun IrCall.stubBlock(stubs: Boolean = false): StubBlock? {
        val block = (arguments.last() as? IrFunctionExpression)?.function
            ?: return fail(this, "The block of '$name' must be a lambda literal")
        return StubBlock(this, block, stubs)
    }

    private fun IrBlockBodyBuilder.irRegistration(block: StubBlock, stubbed: StubbedCall): Registration? {
        val receiver = stubbed.receiver
        if (!chainedMocks && receiver != null && !receiver.isIndependentOf(block.function)) {
            return fail(receiver, "The receiver of the stubbed or verified call must not use matchers")
        }
        val target = targetOf(stubbed.call)
        if (!stubbed.callsSuspend) return irEvaluatedRegistration(block, stubbed, target)
        val anyN = builtIns.anyNType
        val evaluated = irTemporary(
            irEvaluate(block, builtIns.arrayClass.typeWith(anyN)) { nested ->
                val registration = irEvaluatedRegistration(nested, stubbed, target) ?: return null
                irArrayOf(anyN, listOf(irGet(registration.state), registration.matchers))
            }
        )
        return Registration(
            irTemporary(irAs(irArrayGet(evaluated, 0), symbols[CoreClass.MOCK_STATE].defaultType)),
            target.key,
            irAs(irArrayGet(evaluated, 1), matchersType),
        )
    }

    private fun IrBlockBodyBuilder.irEvaluatedRegistration(
        block: StubBlock,
        stubbed: StubbedCall,
        target: Target,
    ): Registration? {
        val receiver = stubbed.receiver?.let { receiver ->
            irTemporary(
                when {
                    chainedMocks -> irLinks(block, receiver) ?: return null
                    receiver.isChain -> irChain(block.dsl, receiver.type) { irLinks(block, receiver) ?: return null }
                    else -> receiver
                }
            )
        }
        val state = irTemporary(
            irStateOf(target.redirect, receiver?.let(::irGet)) ?: return fail(
                stubbed.call,
                "'${stubbed.call.name}' has no interception point: lokki intercepts top-level functions and " +
                    "properties of main compilations with lokki { mockable(...) }",
            )
        )
        val matchers = irArguments(block, stubbed, receiver, state, target.key) ?: return null
        return Registration(state, target.key, matchers)
    }

    private fun IrBlockBodyBuilder.irArguments(
        block: StubBlock,
        stubbed: StubbedCall,
        receiver: IrVariable?,
        state: IrVariable,
        key: String,
    ): IrExpression? {
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
            if (capturesDefaults) placeholderProblem(member, parameter, argument)?.let { return fail(argument, it) }
            val matcher = irArgumentMatcher(argument, declared[parameter.indexInParameters], block, stubbed.allAny)
                ?: return null
            matchers[index] = irTemporary(matcher)
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
                else -> irEq(values[index]?.let(::irGet) ?: irArrayGet(defaults!!, index))
            }
        }
        return irArrayOf(matcherType, arguments)
    }

    private inline fun IrBuilder.irChain(
        dsl: IrCall,
        type: IrType,
        value: IrBlockBodyBuilder.() -> IrExpression,
    ): IrExpression {
        val chain = lambda(dsl, type)
        chain.body = builder(chain, dsl).irBlockBody { +irReturn(value()) }
        return irCompanionCall(symbols[CoreFunction.CHAIN], chain)
    }

    private fun IrBlockBodyBuilder.irLinks(block: StubBlock, receiver: IrExpression): IrExpression? {
        val verification = block.verification
        if (!chainedMocks && verification == null) return receiver
        receiver.wrapped?.let { wrapped ->
            receiver.rewrap(irLinks(block, wrapped) ?: return null)
            return receiver
        }
        val link = receiver.memberCall()?.takeIf { it.call.symbol.owner.isLink }
        if (link == null) {
            if (receiver.isIndependentOf(block.function)) return receiver
            return fail(receiver, "Chained mocks support matchers only in the arguments of a link of the call chain")
        }
        val call = link.call
        val parameters = call.symbol.owner.nonDispatchParameters
        val head = irTemporary(irLinks(block, link.receiver!!) ?: return null)
        val key = call.symbol.owner.memberKeys.first()
        if (link.arguments.any { it?.isIndependentOf(block.function) == false }) {
            return irMatchedLink(block, link, head, key)
        }
        val values = arrayOfNulls<IrVariable>(parameters.size)
        for (index in link.order) values[index] = link.arguments[index]?.let { irTemporary(it) }
        call.dispatchReceiver = irGet(head)
        parameters.forEachIndexed { index, parameter ->
            call.arguments[parameter.indexInParameters] = values[index]?.let(::irGet)
        }
        if (verification != null) {
            +irCall(symbols[CoreFunction.EXPECT_LINK]).apply {
                arguments[0] = irGet(verification)
                arguments[1] = irGet(head)
                arguments[2] = irString(key)
                arguments[3] = irLinkMatchers(block.dsl, link, key, values)
            }
        }
        return if (chainedMocks) irLink(block, head, key, call) else call
    }

    private fun IrBuilderWithScope.irLink(block: StubBlock, head: IrVariable, key: String, call: IrCall): IrExpression {
        val type = call.type
        val child = childOf(type)
        val evaluation = lambda(block.dsl, type)
        evaluation.body = builder(evaluation, block.dsl).irBlockBody { +irReturn(call) }
        return irCall(symbols[CoreFunction.LINK].symbol, type, listOf(type)).apply {
            arguments[0] = irGetObject(symbols[CoreFunction.LINK].parentAsClass)
            arguments[1] = irGet(head)
            arguments[2] = irString(key)
            arguments[3] = irBoolean(block.stubs)
            arguments[4] = child?.let { (mock, types) -> irFactory(symbols, mock, types) } ?: irNull()
            arguments[5] = if (child == null) irString(childProblem(type)) else irNull()
            arguments[6] = evaluation.toLambda(builtIns)
        }
    }

    private fun IrBlockBodyBuilder.irMatchedLink(
        block: StubBlock,
        link: StubbedCall,
        head: IrVariable,
        key: String,
    ): IrExpression? {
        val type = link.call.type
        val (mock, types) = childOf(type) ?: return null.also {
            when (val problem = file.mockability.childProblem(type)) {
                null -> fail(link.call, childProblem(type))
                else -> file.diagnostics.report(link.call, problem.withReason("${problem.reason}. $CHILD_MOCK_NEEDED"))
            }
        }
        val state = irTemporary(irStateOf(irGet(head)))
        val matchers = irTemporary(irArguments(block, link, head, state, targetOf(link.call).key) ?: return null)
        block.verification?.let { verification ->
            +irCall(symbols[CoreFunction.EXPECT_LINK]).apply {
                arguments[0] = irGet(verification)
                arguments[1] = irGet(head)
                arguments[2] = irString(key)
                arguments[3] = irMatchersOf(block.dsl, matchers)
            }
        }
        val child = irCall(symbols[CoreFunction.MOCK_STATE_LINK]).apply {
            arguments[0] = irGet(state)
            arguments[1] = irString(key)
            arguments[2] = irGet(matchers)
            arguments[3] = irBoolean(block.stubs)
            arguments[4] = irFactory(symbols, mock, types)
        }
        return irAs(child, type)
    }

    private fun childOf(type: IrType): Pair<IrClass, MockedTypes>? {
        val site = file.mockability.child(type) ?: return null
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
                irEq(values[index]?.let(::irGet) ?: irArrayGet(defaults!!, index))
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
        return irCall(symbols[CoreFunction.EVALUATE].symbol, type, listOf(type)).apply {
            arguments[0] = irGetObject(symbols[CoreFunction.EVALUATE].parentAsClass)
            arguments[1] = irString(block.dsl.name)
            arguments[2] = evaluation.toLambda(builtIns)
        }
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
            call.arguments[parameter.indexInParameters] = stubbed.arguments[index]?.let { argument ->
                values[index]?.let(::irGet) ?: irPlaceholder(argument, alternate)
            }
        }
        capture.body = builder(capture, dsl).irBlockBody { +call }
        val captureDefaults = if (member.isSuspend) CoreFunction.CO_CAPTURE_DEFAULTS else CoreFunction.CAPTURE_DEFAULTS
        return irCall(symbols[captureDefaults]).apply {
            arguments[0] = irGet(state)
            arguments[1] = irString(key)
            arguments[2] = irArrayOf(
                builtIns.stringType.makeNullable(),
                parameters.mapIndexed { index, parameter ->
                    if (stubbed.arguments[index] == null) irString(parameter.name.asString()) else irNull()
                },
            )
            arguments[3] = capture.toLambda(builtIns)
        }
    }

    private fun fail(element: IrElement, reason: String): Nothing? {
        file.diagnostics.unsupported(element, reason)
        return null
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

    private fun IrBlockBodyBuilder.irArgumentMatcher(
        argument: IrExpression,
        declared: IrValueParameter,
        block: StubBlock,
        allAny: Boolean,
    ): IrExpression? {
        val arrayType = declared.type.erasedUpperBound
        if (argument !is IrVararg) return irMatcher(argument, arrayType, block)
        return irElementsMatcher(argument, declared.varargElementType!!.erasedUpperBound, arrayType, block, allAny)
    }

    private fun IrBlockBodyBuilder.irElementsMatcher(
        argument: IrVararg?,
        elementType: IrClass,
        arrayType: IrClass,
        block: StubBlock,
        allAny: Boolean,
    ): IrExpression? {
        val parts = argument?.elements.orEmpty()
        val spreadMatchers = parts.filterIsInstance<IrSpreadElement>()
            .filterNot { it.expression.isIndependentOf(block.function) }
        if (spreadMatchers.size > 1) {
            return fail(spreadMatchers[1].expression, "A vararg argument cannot contain more than one vararg matcher")
        }
        val spreadMatcher = spreadMatchers.singleOrNull()
        val elements = parts.map { element ->
            when {
                element !is IrSpreadElement ->
                    irMatcher(element as IrExpression, elementType, block, allAny) ?: return null
                element === spreadMatcher -> irSpreadMatcher(element.expression, arrayType, block) ?: return null
                else -> IrSpreadElementImpl(
                    startOffset,
                    endOffset,
                    irCall(symbols.matcherFactory(MatcherNames.SPREAD_FACTORY)).apply {
                        arguments[0] = irGetObject(symbols[CoreClass.MATCHERS])
                        arguments[1] = element.expression
                    },
                )
            }
        }
        if (spreadMatcher == null) {
            return irCallConstructor(symbols[CoreConstructor.ARRAY_MATCHER].symbol, emptyList()).apply {
                arguments[0] = irArrayOf(matcherType, elements)
            }
        }
        val position = parts.indexOf(spreadMatcher)
        return irCall(symbols.matcherFactory(MatcherNames.VARARG_OF_FACTORY)).apply {
            arguments[0] = irGetObject(symbols[CoreClass.MATCHERS])
            arguments[1] = irArrayOf(matcherType, elements.subList(0, position))
            arguments[2] = elements[position] as IrExpression
            arguments[3] = irArrayOf(matcherType, elements.subList(position + 1, elements.size))
        }
    }

    private fun IrBlockBodyBuilder.irSpreadMatcher(
        matcher: IrExpression,
        arrayType: IrClass,
        block: StubBlock,
    ): IrExpression? {
        if (matcher.isBuiltinMatcher && (matcher as IrCall).name !in MatcherNames.VARARG) {
            return fail(
                matcher,
                "The matcher '${matcher.name}' cannot be spread into a vararg parameter: " +
                    "only 'anyVararg', 'varargAll' and 'varargAny' match a part of the array",
            )
        }
        return irMatcher(matcher, arrayType, block)
    }

    private fun IrBlockBodyBuilder.irMatcher(
        argument: IrExpression,
        declared: IrClass,
        block: StubBlock,
        allAny: Boolean = false,
    ): IrExpression? =
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

    private fun IrBlockBodyBuilder.irBuiltinMatcher(call: IrCall, declared: IrClass, block: StubBlock): IrExpression? {
        val name = call.name
        val matcher = when (name) {
            in MatcherNames.ANY -> irAny()
            in MatcherNames.COMPOSITE -> irFactory(call) { irMatcher(it, declared, block) }
            MatcherNames.ARRAY -> {
                val elements = call.symbol.owner.parameters.single { it.varargElementType != null }
                val argument = call.arguments[elements.indexInParameters] as IrVararg?
                irElementsMatcher(argument, builtIns.anyClass.owner, declared, block, allAny = false)
            }
            else -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(block.function) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
        } ?: return null
        val type = call.typeArguments.firstOrNull() ?: return matcher
        if (name !in MatcherNames.TYPED || !type.isStrictSubtypeOf(declared)) return matcher
        return irCall(symbols.matcherFactory(MatcherNames.TYPED_FACTORY)).apply {
            typeArguments[0] = type
            arguments[0] = irGetObject(symbols[CoreClass.MATCHERS])
            arguments[1] = matcher
        }
    }

    private inline fun IrBuilder.irFactory(call: IrCall, argument: (IrExpression) -> IrExpression?): IrExpression? {
        val function = call.symbol.owner
        val factory = symbols.matcherFactory(function)
        return irCall(factory).apply {
            if (factory.typeParameters.isNotEmpty()) typeArguments[0] = call.typeArguments[0]
            arguments[0] = irGetObject(symbols[CoreClass.MATCHERS])
            function.nonDispatchParameters.forEachIndexed { index, parameter ->
                val passed = call.arguments[parameter.indexInParameters] ?: return@forEachIndexed
                arguments[index + 1] = argument(passed) ?: return null
            }
        }
    }

    private fun IrBlockBodyBuilder.irHelperMatcher(helper: IrExpression, block: StubBlock): IrExpression {
        val scope = block.scope ?: irTemporary(
            irCallConstructor(symbols[CoreConstructor.REGISTRATION_SCOPE].symbol, emptyList()),
            irType = block.receiverType,
        ).also { block.scope = it }
        val value = helper.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression =
                    if (expression.isScopeOf(block.function)) irGet(scope) else expression
            },
            null,
        )
        return irCall(symbols[CoreFunction.MATCHER_OF]).apply {
            arguments[0] = irGet(scope)
            arguments[1] = value
        }
    }

    private fun IrBuilder.irAny(): IrExpression = irGetObject(symbols[CoreClass.ANY_MATCHER])

    private fun targetOf(call: IrCall): Target {
        val member = call.symbol.owner
        val redirected = file.redirects.member(member)
        return Target(redirected?.redirect, redirected?.key ?: member.memberKeys.first())
    }

    private fun IrBuilder.irStateOf(redirect: Redirect?, receiver: IrExpression?): IrExpression? {
        if (redirect == null) return receiver?.let { irStateOf(it) }
        val (of, target) = when (redirect) {
            is ObjectRedirect -> symbols[CoreFunction.REDIRECT_OF] to receiver!!
            is StaticRedirect -> symbols[CoreFunction.STATIC_REDIRECT_OF] to irString(redirect.key)
        }
        return irCall(symbols.state.getter!!).apply {
            arguments[0] = irCall(of).apply {
                arguments[0] = irGetObject(symbols[CoreClass.REDIRECTS])
                arguments[1] = target
                arguments[2] = irString(redirect.name)
            }
        }
    }

    private fun IrBuilder.irStateOf(instance: IrExpression): IrExpression =
        irCall(symbols.state.getter!!).apply {
            arguments[0] = irCall(symbols[CoreFunction.OF]).apply {
                arguments[0] = irGetObject(symbols[CoreFunction.OF].parentAsClass)
                arguments[1] = instance
            }
        }

    private fun IrBuilder.irEq(value: IrExpression): IrExpression =
        irCallConstructor(symbols[CoreConstructor.EQ_MATCHER].symbol, emptyList()).apply { arguments[0] = value }

    private fun IrBuilder.irArrayGet(array: IrVariable, index: Int): IrExpression =
        irCall(symbols.arrayGet.symbol, builtIns.anyNType).apply {
            arguments[0] = irGet(array)
            arguments[1] = irInt(index)
        }

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
}

private class Registration(val state: IrVariable, val key: String, val matchers: IrExpression)

private class Target(val redirect: Redirect?, val key: String)

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
