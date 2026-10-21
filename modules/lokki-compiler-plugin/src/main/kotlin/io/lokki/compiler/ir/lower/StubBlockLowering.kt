package io.lokki.compiler.ir.lower

import io.lokki.compiler.ALL_ANY
import io.lokki.compiler.CAPTURE_MATCHERS
import io.lokki.compiler.COMPOSITE_MATCHERS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.JUST_AWAIT_BLOCK
import io.lokki.compiler.JUST_RUN_BLOCKS
import io.lokki.compiler.WITH_ARG_MATCHERS
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.memberKeys
import io.lokki.compiler.ir.root
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
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
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.SpecialNames

internal class StubBlockLowering(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val unsupported: (IrElement, String) -> Unit,
) {
    private val matcherType = symbols.matcher.typeWith(context.irBuiltIns.anyNType)

    fun lowerStub(every: IrCall, scope: Scope): IrExpression = stub(every, scope) ?: every

    fun lowerVerify(verify: IrCall, scope: Scope): IrExpression = verify(verify, scope) ?: verify

    fun lowerExclusion(exclude: IrCall, scope: Scope): IrExpression = exclusion(exclude, scope) ?: exclude

    fun lowerAwaits(just: IrCall, scope: Scope): IrExpression {
        val awaitCancellation = awaitCancellation(just, "${just.name} Awaits") ?: return just
        val answers = just.symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
        val answersClass = answers.type.erasedUpperBound
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, just.startOffset, just.endOffset)
            .irAwaits(just, just.arguments[answers.indexInParameters]!!, answersClass, awaitCancellation)
            .patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    fun noCallsStatements(verify: IrCall): List<IrCall> =
        (verify.arguments.last() as? IrFunctionExpression)?.function?.blockStatements().orEmpty()
            .mapNotNull { it.noCalls()?.call }

    private fun stub(every: IrCall, scope: Scope): IrExpression? {
        val block = every.stubBlock() ?: return null
        val stubbed = block.function.stubbedCall()
            ?: return fail(block.function, "The block of '${every.name}' must be a single call of a mock member")

        val justRuns = every.name in JUST_RUN_BLOCKS
        val awaitCancellation = when (every.name) {
            JUST_AWAIT_BLOCK -> awaitCancellation(every, every.name) ?: return null
            else -> null
        }
        val just = justRuns || awaitCancellation != null
        val answered = if (just) context.irBuiltIns.unitType else every.typeArguments[0]!!
        val stubType = if (just) symbols.stubScope.typeWith(answered) else every.type
        val registration = lambda(every, stubType)
        registration.body = DeclarationIrBuilder(context, registration.symbol, every.startOffset, every.endOffset)
            .irBlockBody {
                val call = irRegistration(every, block, stubbed) ?: return null
                +irReturn(
                    irCall(symbols.stub.symbol, stubType, listOf(answered)).apply {
                        arguments[0] = irGet(call.state)
                        arguments[1] = irString(call.key)
                        arguments[2] = call.matchers
                    }
                )
            }

        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, every.startOffset, every.endOffset).run {
            val stub = irWithoutStateChanges(registration, stubType)
            when {
                awaitCancellation != null -> irAwaits(every, stub, symbols.stubScope, awaitCancellation)
                justRuns -> irCall(symbols.returns.symbol, every.type).apply {
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
        val answerScope = symbols.answerScope.typeWith(type)
        val call = symbols.call.defaultType
        val answer = lambda(dsl, type, suspend = true)
        answer.addValueParameter("scope", answerScope)
        answer.addValueParameter("call", call)
        answer.body = DeclarationIrBuilder(context, answer.symbol, dsl.startOffset, dsl.endOffset)
            .irBlockBody { +irReturn(irCall(awaitCancellation)) }
        val answerWith = if (answersClass == symbols.stubScope) symbols.coAnswers else symbols.coAndThen
        return irCall(answerWith.symbol, dsl.type).apply {
            arguments[0] = answers
            arguments[1] = answer.expression(answerScope, call, type)
        }
    }

    private fun verify(verify: IrCall, scope: Scope): IrExpression? {
        val block = verify.stubBlock() ?: return null
        val expected = block.function.verifiedStatements()
            ?: return fail(block.function, "The block of '${verify.name}' must consist of calls of mock members")
        expected.filterIsInstance<StubbedCall>().find { it.call.symbol.owner.isMethodOfAny() }?.let {
            val reason = "calls of equals, hashCode and toString cannot be verified"
            return fail(it.call, "'${it.call.name}' is not recorded: $reason")
        }

        val unit = context.irBuiltIns.unitType
        val check = lambda(verify, unit)
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, verify.startOffset, verify.endOffset).irBlock {
            val verification = irTemporary(irVerification(verify))
            check.body = DeclarationIrBuilder(context, check.symbol, verify.startOffset, verify.endOffset).irBlockBody {
                for (expectation in expected) {
                    when (expectation) {
                        is NoCalls -> {
                            val mocks = expectation.mocks
                            if (!mocks.isIndependentOf(block.function)) {
                                return fail(mocks, "The receiver of 'wasNot Called' must not use matchers")
                            }
                            val evaluated = when {
                                mocks.callsSuspend() -> irEvaluate(verify, block, mocks.type) { mocks }
                                else -> mocks
                            }
                            +irCall(symbols.expectNoCalls(expectation.mocksClass)).apply {
                                arguments[0] = irGet(verification)
                                arguments[1] = evaluated
                            }
                        }
                        is StubbedCall -> {
                            val call = irRegistration(verify, block, expectation, verification) ?: return null
                            +irCall(symbols.expect).apply {
                                arguments[0] = irGet(verification)
                                arguments[1] = irGet(call.state)
                                arguments[2] = irString(call.key)
                                arguments[3] = call.matchers
                            }
                        }
                    }
                }
                +irCall(symbols.check).apply { arguments[0] = irGet(verification) }
            }
            +irWithoutStateChanges(check, unit)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun exclusion(exclude: IrCall, scope: Scope): IrExpression? {
        val block = exclude.stubBlock() ?: return null
        val excluded = block.function.excludedCalls()
            ?: return fail(block.function, "The block of '${exclude.name}' must consist of calls of mock members")

        val unit = context.irBuiltIns.unitType
        val registration = lambda(exclude, unit)
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, exclude.startOffset, exclude.endOffset).irBlock {
            val current = exclude.arguments[0]?.let { irTemporary(it) }
            registration.body = DeclarationIrBuilder(context, registration.symbol, exclude.startOffset, exclude.endOffset)
                .irBlockBody {
                    for (stubbed in excluded) {
                        val call = irRegistration(exclude, block, stubbed) ?: return null
                        +irCall(symbols.exclude).apply {
                            arguments[0] = irGet(call.state)
                            arguments[1] = irString(call.key)
                            arguments[2] = call.matchers
                            arguments[3] = current?.let(::irGet)
                        }
                    }
                }
            +irWithoutStateChanges(registration, unit)
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun IrBuilder.irVerification(verify: IrCall): IrExpression {
        val function = verify.symbol.owner
        val calls = function.parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
        if (calls != null) {
            return irCallConstructor(symbols.verificationOf(calls.type.erasedUpperBound).symbol, emptyList()).apply {
                arguments[0] = verify.arguments[calls.indexInParameters]
            }
        }
        val constructor = symbols.verification.primaryConstructor!!
        return irCallConstructor(constructor.symbol, emptyList()).apply {
            for (parameter in constructor.parameters) {
                val argument = function.parameters.find { it.name == parameter.name } ?: continue
                arguments[parameter.indexInParameters] = verify.arguments[argument.indexInParameters]
            }
        }
    }

    private fun IrCall.stubBlock(): StubBlock? {
        val block = (arguments.last() as? IrFunctionExpression)?.function
            ?: return fail(this, "The block of '$name' must be a lambda literal")
        return StubBlock(block)
    }

    private fun IrBlockBodyBuilder.irRegistration(
        dsl: IrCall,
        block: StubBlock,
        stubbed: StubbedCall,
        verification: IrVariable? = null,
    ): Registration? {
        if (!stubbed.receiver.isIndependentOf(block.function)) {
            return fail(stubbed.receiver, "The receiver of the stubbed or verified call must not use matchers")
        }
        if (!stubbed.callsSuspend) return irEvaluatedRegistration(dsl, block, stubbed, verification)
        val anyN = context.irBuiltIns.anyNType
        val evaluated = irTemporary(
            irEvaluate(dsl, block, context.irBuiltIns.arrayClass.typeWith(anyN)) {
                val registration = irEvaluatedRegistration(dsl, block, stubbed, verification) ?: return null
                irArrayOf(anyN, listOf(irGet(registration.state), registration.matchers))
            }
        )
        return Registration(
            irTemporary(irAs(irArrayGet(evaluated, 0), symbols.mockState.defaultType)),
            stubbed.call.symbol.owner.memberKeys.first(),
            irAs(irArrayGet(evaluated, 1), context.irBuiltIns.arrayClass.typeWith(matcherType)),
        )
    }

    private fun IrBlockBodyBuilder.irEvaluatedRegistration(
        dsl: IrCall,
        block: StubBlock,
        stubbed: StubbedCall,
        verification: IrVariable?,
    ): Registration? {
        val call = stubbed.call
        val member = call.symbol.owner
        val parameters = member.nonDispatchParameters
        val declared = member.root.parameters
        val capturesDefaults = !stubbed.allAny && null in stubbed.arguments

        val receiver = irTemporary(verification?.let { irLinks(dsl, stubbed.receiver, it) } ?: stubbed.receiver)
        val state = irTemporary(irStateOf(irGet(receiver)))
        val key = member.memberKeys.first()
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
            call.dispatchReceiver = irGet(receiver)
            irTemporary(irCaptureDefaults(dsl, state, key, stubbed, values))
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
        return Registration(state, key, irArrayOf(matcherType, arguments))
    }

    private fun IrBlockBodyBuilder.irLinks(
        dsl: IrCall,
        receiver: IrExpression,
        verification: IrVariable,
    ): IrExpression {
        when {
            receiver is IrTypeOperatorCall && receiver.operator in CASTS -> {
                receiver.argument = irLinks(dsl, receiver.argument, verification)
                return receiver
            }
            receiver is IrCall && receiver.origin == IrStatementOrigin.EXCLEXCL -> {
                receiver.arguments[0] = irLinks(dsl, receiver.arguments[0]!!, verification)
                return receiver
            }
        }
        val link = receiver.memberCall()?.takeIf { it.call.symbol.owner.isLink } ?: return receiver
        val call = link.call
        val parameters = call.symbol.owner.nonDispatchParameters
        val head = irTemporary(irLinks(dsl, link.receiver, verification))
        val values = arrayOfNulls<IrVariable>(parameters.size)
        for (index in link.order) values[index] = link.arguments[index]?.let { irTemporary(it) }
        call.dispatchReceiver = irGet(head)
        parameters.forEachIndexed { index, parameter ->
            call.arguments[parameter.indexInParameters] = values[index]?.let(::irGet)
        }
        val key = call.symbol.owner.memberKeys.first()
        +irCall(symbols.expectLink).apply {
            arguments[0] = irGet(verification)
            arguments[1] = irGet(head)
            arguments[2] = irString(key)
            arguments[3] = irLinkMatchers(dsl, link, key, values)
        }
        return call
    }

    private fun IrBuilder.irLinkMatchers(
        dsl: IrCall,
        link: StubbedCall,
        key: String,
        values: Array<IrVariable?>,
    ): IrExpression {
        val stateType = symbols.mockState.defaultType
        val arrayType = context.irBuiltIns.arrayClass.typeWith(matcherType)
        val matchers = lambda(dsl, arrayType)
        val state = matchers.addValueParameter("state", stateType)
        matchers.body = DeclarationIrBuilder(context, matchers.symbol, startOffset, endOffset).irBlockBody {
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
        return matchers.expression(stateType, arrayType)
    }

    private inline fun IrBuilderWithScope.irEvaluate(
        dsl: IrCall,
        block: StubBlock,
        type: IrType,
        value: IrBlockBodyBuilder.() -> IrExpression,
    ): IrExpression {
        val evaluation = lambda(dsl, type, suspend = true)
        val scope = block.scope
        block.scope = null
        evaluation.body = DeclarationIrBuilder(context, evaluation.symbol, dsl.startOffset, dsl.endOffset)
            .irBlockBody { +irReturn(value()) }
        block.scope = scope
        return irCall(symbols.evaluate.symbol, type, listOf(type)).apply {
            val companion = symbols.evaluate.parentAsClass
            arguments[0] = irGetObjectValue(companion.defaultType, companion.symbol)
            arguments[1] = irString(dsl.name)
            arguments[2] = evaluation.expression(type)
        }
    }

    private fun IrBuilder.irWithoutStateChanges(registration: IrSimpleFunction, type: IrType): IrExpression =
        irCall(symbols.withoutStateChanges.symbol, type, listOf(type)).apply {
            val companion = symbols.withoutStateChanges.parentAsClass
            arguments[0] = irGetObjectValue(companion.defaultType, companion.symbol)
            arguments[1] = registration.expression(type)
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
        val capture = lambda(dsl, context.irBuiltIns.unitType, suspend = member.isSuspend)
        val alternate = capture.addValueParameter("alternate", context.irBuiltIns.booleanType)
        parameters.forEachIndexed { index, parameter ->
            call.arguments[parameter.indexInParameters] = stubbed.arguments[index]?.let { argument ->
                values[index]?.let(::irGet) ?: irPlaceholder(argument, alternate)
            }
        }
        capture.body = DeclarationIrBuilder(context, capture.symbol, startOffset, endOffset).irBlockBody { +call }
        return irCall(if (member.isSuspend) symbols.coCaptureDefaults else symbols.captureDefaults).apply {
            arguments[0] = irGet(state)
            arguments[1] = irString(key)
            arguments[2] = irArrayOf(
                context.irBuiltIns.stringType.makeNullable(),
                parameters.mapIndexed { index, parameter ->
                    if (stubbed.arguments[index] == null) irString(parameter.name.asString()) else irNull()
                },
            )
            arguments[3] = capture.expression(context.irBuiltIns.booleanType, capture.returnType)
        }
    }

    private fun fail(element: IrElement, reason: String): Nothing? {
        unsupported(element, reason)
        return null
    }

    private fun lambda(dsl: IrCall, type: IrType, suspend: Boolean = false): IrSimpleFunction =
        context.irFactory.buildFun {
            startOffset = dsl.startOffset
            endOffset = dsl.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = type
            isSuspend = suspend
        }

    private fun IrSimpleFunction.expression(vararg types: IrType): IrExpression {
        val arity = types.size - 1
        val type = if (isSuspend) context.irBuiltIns.suspendFunctionN(arity) else context.irBuiltIns.functionN(arity)
        return IrFunctionExpressionImpl(startOffset, endOffset, type.typeWith(*types), this, IrStatementOrigin.LAMBDA)
    }

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
        val elementType = declared.varargElementType!!.erasedUpperBound
        val spreadMatchers = argument.elements.filterIsInstance<IrSpreadElement>()
            .filterNot { it.expression.isIndependentOf(block.function) }
        if (spreadMatchers.size > 1) {
            return fail(spreadMatchers[1].expression, "A vararg argument cannot contain more than one vararg matcher")
        }
        val spreadMatcher = spreadMatchers.singleOrNull()
        val elements = argument.elements.map { element ->
            when {
                element !is IrSpreadElement ->
                    irMatcher(element as IrExpression, elementType, block, allAny) ?: return null
                element === spreadMatcher -> irSpreadMatcher(element.expression, arrayType, block) ?: return null
                else -> IrSpreadElementImpl(
                    startOffset,
                    endOffset,
                    irCall(symbols.matcherFactory(SPREAD)).apply {
                        arguments[0] = irMatchers()
                        arguments[1] = element.expression
                    },
                )
            }
        }
        if (spreadMatcher == null) {
            return irCallConstructor(symbols.arrayMatcher.primaryConstructor!!.symbol, emptyList()).apply {
                arguments[0] = irArrayOf(matcherType, elements)
            }
        }
        val position = argument.elements.indexOf(spreadMatcher)
        return irCall(symbols.matcherFactory(VARARG_OF)).apply {
            arguments[0] = irMatchers()
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
        if (matcher.isBuiltinMatcher && (matcher as IrCall).name !in VARARG_MATCHERS) {
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
            in ANY_MATCHERS -> irAny()
            in COMPOSITE_MATCHERS -> irFactory(call) { irMatcher(it, declared, block) }
            else -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(block.function) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
        } ?: return null
        val type = call.typeArguments.firstOrNull() ?: return matcher
        if (name !in TYPED_MATCHERS || !type.isStrictSubtypeOf(declared)) return matcher
        return irCall(symbols.matcherFactory(TYPED)).apply {
            typeArguments[0] = type
            arguments[0] = irMatchers()
            arguments[1] = matcher
        }
    }

    private inline fun IrBuilder.irFactory(call: IrCall, argument: (IrExpression) -> IrExpression?): IrExpression? {
        val function = call.symbol.owner
        val factory = symbols.matcherFactory(function)
        return irCall(factory).apply {
            if (factory.typeParameters.isNotEmpty()) typeArguments[0] = call.typeArguments[0]
            arguments[0] = irMatchers()
            function.nonDispatchParameters.forEachIndexed { index, parameter ->
                val passed = call.arguments[parameter.indexInParameters] ?: return@forEachIndexed
                arguments[index + 1] = argument(passed) ?: return null
            }
        }
    }

    private fun IrBlockBodyBuilder.irHelperMatcher(helper: IrExpression, block: StubBlock): IrExpression {
        val scope = block.scope ?: irTemporary(
            irCallConstructor(symbols.registrationScope.primaryConstructor!!.symbol, emptyList()),
            irType = block.receiverType,
        ).also { block.scope = it }
        val value = helper.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression =
                    if (expression.isScopeOf(block.function)) irGet(scope) else expression
            },
            null,
        )
        return irCall(symbols.matcherOf).apply {
            arguments[0] = irGet(scope)
            arguments[1] = value
        }
    }

    private fun IrBuilder.irMatchers(): IrExpression =
        irGetObjectValue(symbols.matchers.defaultType, symbols.matchers.symbol)

    private fun IrBuilder.irAny(): IrExpression =
        irGetObjectValue(symbols.anyMatcher.defaultType, symbols.anyMatcher.symbol)

    private fun IrBuilder.irStateOf(instance: IrExpression): IrExpression =
        irCall(symbols.state.getter!!).apply {
            arguments[0] = irCall(symbols.of).apply {
                val companion = symbols.of.parentAsClass
                arguments[0] = irGetObjectValue(companion.defaultType, companion.symbol)
                arguments[1] = instance
            }
        }

    private fun IrBuilder.irEq(value: IrExpression): IrExpression =
        irCallConstructor(symbols.eqMatcher.primaryConstructor!!.symbol, emptyList()).apply { arguments[0] = value }

    private fun IrBuilder.irArrayGet(array: IrVariable, index: Int): IrExpression =
        irCall(symbols.arrayGet.symbol, context.irBuiltIns.anyNType).apply {
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

private class StubBlock(val function: IrSimpleFunction) {
    var scope: IrVariable? = null

    val receiverType: IrType
        get() = function.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }.type
}

private sealed interface Expected

private class StubbedCall(
    val call: IrCall,
    val receiver: IrExpression,
    val arguments: List<IrExpression?>,
    val order: List<Int>,
) : Expected {
    val allAny: Boolean
        get() = arguments.firstOrNull().isAllAny

    val callsSuspend: Boolean
        get() = receiver.callsSuspend() || arguments.any { it?.callsSuspend() == true }
}

private class NoCalls(val call: IrCall) : Expected {
    private val receiver = call.symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }

    val mocks: IrExpression
        get() = call.arguments[receiver.indexInParameters]!!

    val mocksClass: IrClass
        get() = receiver.type.erasedUpperBound
}

private class Registration(val state: IrVariable, val key: String, val matchers: IrExpression)

private val IrCall.name: String
    get() = symbol.owner.name.asString()

private fun IrSimpleFunction.stubbedCall(): StubbedCall? =
    (body as? IrBlockBody)?.statements?.singleOrNull()?.stubbedCall()

private fun IrSimpleFunction.verifiedStatements(): List<Expected>? {
    val statements = blockStatements()
    if (statements.isEmpty()) return null
    return statements.map { it.noCalls() ?: it.stubbedCall() ?: return null }
}

private fun IrSimpleFunction.excludedCalls(): List<StubbedCall>? {
    val statements = blockStatements()
    if (statements.isEmpty()) return null
    return statements.map { it.stubbedCall() ?: return null }
}

private fun IrSimpleFunction.blockStatements(): List<IrStatement> =
    (body as? IrBlockBody)?.statements.orEmpty().filterNot { it.isUnit }

private val IrStatement.isUnit: Boolean
    get() {
        val value = if (this is IrReturn) value else this
        return value is IrGetObjectValue && value.type.isUnit()
    }

private fun IrStatement.stubbedCall(): StubbedCall? = (unwrapped() as? IrExpression)?.memberCall()

private fun IrStatement.noCalls(): NoCalls? =
    (unwrapped() as? IrCall)?.takeIf { it.intrinsicKind == IntrinsicKind.WAS_NOT_CALLED }?.let(::NoCalls)

private fun IrStatement.unwrapped(): IrStatement? {
    var expression: IrStatement? = if (this is IrReturn) value else this
    while (true) {
        expression = when {
            expression is IrTypeOperatorCall && expression.operator in CASTS -> expression.argument
            expression is IrCall && expression.origin == IrStatementOrigin.EXCLEXCL -> expression.arguments[0]
            else -> return expression
        }
    }
}

private fun IrExpression.memberCall(): StubbedCall? {
    var expression: IrStatement? = this
    var frozen = emptyList<IrVariable>()
    if (expression is IrBlock && expression.origin == IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL) {
        frozen = expression.statements.filterIsInstance<IrVariable>()
        expression = expression.statements.last()
    }
    val call = expression as? IrCall ?: return null
    fun frozenIndex(argument: IrExpression?) = frozen.indexOfFirst { it.symbol == (argument as? IrGetValue)?.symbol }
    fun IrExpression.thawed() = frozen.getOrNull(frozenIndex(this))?.initializer ?: this
    val receiver = call.dispatchReceiver?.thawed() ?: return null
    val arguments = call.symbol.owner.nonDispatchParameters.map { call.arguments[it.indexInParameters] }
    return StubbedCall(
        call,
        receiver,
        arguments.map { it?.thawed() },
        arguments.indices.sortedBy { index -> frozenIndex(arguments[index]).takeIf { it >= 0 } ?: frozen.size },
    )
}

private val IrSimpleFunction.isLink: Boolean
    get() = modality != Modality.FINAL && !isMethodOfAny()

private val IrExpression?.isAllAny: Boolean
    get() = when (this) {
        is IrVararg -> (elements.firstOrNull() as? IrExpression).isAllAny
        is IrCall -> intrinsicKind == IntrinsicKind.MATCHER && symbol.owner.name.asString() == ALL_ANY
        else -> false
    }

private val IrExpression.isBuiltinMatcher: Boolean
    get() = this is IrCall && intrinsicKind == IntrinsicKind.MATCHER && symbol.owner.name.asString() in BUILTIN_MATCHERS

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

private fun IrExpression.callsSuspend(): Boolean {
    var found = false
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            when {
                found -> {}
                element is IrCall && element.symbol.owner.isSuspend -> found = true
                element is IrFunctionExpression && element.function.isSuspend -> {}
                else -> element.acceptChildrenVoid(this)
            }
        }
    })
    return found
}

private fun IrExpression.contains(predicate: (IrElement) -> Boolean): Boolean {
    var found = false
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            found = found || predicate(element)
            if (!found) element.acceptChildrenVoid(this)
        }
    })
    return found
}

private val CASTS = setOf(
    IrTypeOperator.CAST,
    IrTypeOperator.IMPLICIT_CAST,
    IrTypeOperator.IMPLICIT_NOTNULL,
    IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
)

private const val TYPED = "typed"
private const val SPREAD = "spread"
private const val VARARG_OF = "varargOf"
private val ANY_MATCHERS = setOf("any", "anyNullable", ALL_ANY)
private val COMPARISON_MATCHERS = setOf("cmpEq", "more", "less", "range")
private val TYPED_MATCHERS =
    setOf("any", "anyNullable", "match", "matchNullable", "coMatch", "coMatchNullable") +
        setOf("captureLambda", "captureCoroutine") + WITH_ARG_MATCHERS + COMPARISON_MATCHERS + CAPTURE_MATCHERS
private val VARARG_MATCHERS =
    setOf("anyVararg", "varargAll", "varargAllNullable", "varargAny", "varargAnyNullable") +
        listOf("Boolean", "Byte", "Char", "Short", "Int", "Long", "Float", "Double")
            .flatMap { listOf("any${it}Vararg", "varargAll$it", "varargAny$it") }
private val BUILTIN_MATCHERS =
    TYPED_MATCHERS + COMPOSITE_MATCHERS + VARARG_MATCHERS +
        setOf(ALL_ANY, "eq", "neq", "refEq", "nrefEq", "isNull", "ofType")
