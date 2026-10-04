package io.lokki.compiler.ir.lower

import io.lokki.compiler.ir.IntrinsicKind
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.memberKeys
import io.lokki.compiler.ir.root
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
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
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.functions
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

    fun lower(every: IrCall, scope: Scope): IrExpression = stub(every, scope) ?: every

    private fun stub(every: IrCall, scope: Scope): IrExpression? {
        val function = every.symbol.owner.name
        val block = (every.arguments[0] as? IrFunctionExpression)?.function
            ?: return fail(every, "The block of '$function' must be a lambda literal")
        val stubbed = block.stubbedCall()
            ?: return fail(block, "The block of '$function' must be a single call of a mock member")
        val call = stubbed.call
        val member = call.symbol.owner
        if (!stubbed.receiver.isIndependentOf(block)) {
            return fail(stubbed.receiver, "The receiver of the stubbed call must not use matchers")
        }
        val parameters = member.nonDispatchParameters
        val declared = member.root.parameters
        val stub = StubBlock(block, stubbed.arguments.firstOrNull().isAllAny)
        val capturesDefaults = !stub.allAny && null in stubbed.arguments

        val registration = lambda(every, every.type)
        registration.body = DeclarationIrBuilder(context, registration.symbol, every.startOffset, every.endOffset)
            .irBlockBody {
                val receiver = irTemporary(stubbed.receiver)
                val state = irTemporary(irStateOf(irGet(receiver)))
                val key = member.memberKeys.first()
                val values = arrayOfNulls<IrVariable>(parameters.size)
                val matchers = arrayOfNulls<IrVariable>(parameters.size)
                for (index in stubbed.order) {
                    val argument = stubbed.arguments[index] ?: continue
                    val parameter = parameters[index]
                    if (argument.isIndependentOf(block)) {
                        values[index] = irTemporary(argument)
                        continue
                    }
                    if (capturesDefaults) placeholderProblem(parameter, argument)?.let { return fail(argument, it) }
                    val matcher = irArgumentMatcher(argument, declared[parameter.indexInParameters], stub)
                        ?: return null
                    matchers[index] = irTemporary(matcher)
                }
                val defaults = if (capturesDefaults) {
                    call.dispatchReceiver = irGet(receiver)
                    irTemporary(irCaptureDefaults(every, state, key, stubbed, values))
                } else {
                    null
                }
                +irReturn(
                    irCall(symbols.stub.symbol, every.type, listOf(every.typeArguments[0]!!)).apply {
                        arguments[0] = irGet(state)
                        arguments[1] = irString(key)
                        arguments[2] = irArrayOf(
                            matcherType,
                            parameters.indices.map { index ->
                                when {
                                    matchers[index] != null -> irGet(matchers[index]!!)
                                    stub.allAny -> irAny()
                                    else -> irEq(values[index]?.let(::irGet) ?: irArrayGet(defaults!!, index))
                                }
                            },
                        )
                    }
                )
            }

        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, every.startOffset, every.endOffset).run {
            irCall(symbols.withoutStateChanges.symbol, every.type, listOf(every.type)).apply {
                val companion = symbols.withoutStateChanges.parentAsClass
                arguments[0] = irGetObjectValue(companion.defaultType, companion.symbol)
                arguments[1] = registration.expression(every.type)
            }
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun IrBuilder.irCaptureDefaults(
        every: IrCall,
        state: IrVariable,
        key: String,
        stubbed: StubbedCall,
        values: Array<IrVariable?>,
    ): IrExpression {
        val call = stubbed.call
        val parameters = call.symbol.owner.nonDispatchParameters
        val capture = lambda(every, context.irBuiltIns.unitType)
        val alternate = capture.addValueParameter("alternate", context.irBuiltIns.booleanType)
        parameters.forEachIndexed { index, parameter ->
            call.arguments[parameter.indexInParameters] = stubbed.arguments[index]?.let { argument ->
                values[index]?.let(::irGet) ?: irPlaceholder(argument, alternate)
            }
        }
        capture.body = DeclarationIrBuilder(context, capture.symbol, startOffset, endOffset).irBlockBody { +call }
        return irCall(symbols.captureDefaults).apply {
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

    private fun lambda(every: IrCall, type: IrType): IrSimpleFunction =
        context.irFactory.buildFun {
            startOffset = every.startOffset
            endOffset = every.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = type
        }

    private fun IrSimpleFunction.expression(vararg types: IrType): IrExpression =
        IrFunctionExpressionImpl(
            startOffset,
            endOffset,
            context.irBuiltIns.functionN(types.size - 1).typeWith(*types),
            this,
            IrStatementOrigin.LAMBDA,
        )

    private fun placeholderProblem(parameter: IrValueParameter, argument: IrExpression): String? =
        when {
            argument.type.classOrNull?.owner?.isValue == true ->
                "A matcher of a value class cannot be combined with omitted arguments: pass all arguments explicitly"
            parameter.kind == IrParameterKind.ExtensionReceiver &&
                !parameter.type.isNullable() && !argument.type.isPrimitiveType() ->
                "A matcher of the extension receiver cannot be combined with omitted arguments: " +
                    "pass all arguments explicitly"
            else -> null
        }

    private fun IrBlockBodyBuilder.irArgumentMatcher(
        argument: IrExpression,
        declared: IrValueParameter,
        stub: StubBlock,
    ): IrExpression? {
        if (argument !is IrVararg) return irMatcher(argument, declared.type.erasedUpperBound, stub)
        val elementType = declared.varargElementType!!.erasedUpperBound
        val elements = argument.elements.map { element ->
            when {
                element !is IrSpreadElement ->
                    irMatcher(element as IrExpression, elementType, stub, stub.allAny) ?: return null
                !element.expression.isIndependentOf(stub.block) ->
                    return fail(element.expression, "A matcher cannot be spread into a vararg parameter")
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
        return irCallConstructor(symbols.arrayMatcher.primaryConstructor!!.symbol, emptyList()).apply {
            arguments[0] = irArrayOf(matcherType, elements)
        }
    }

    private fun IrBlockBodyBuilder.irMatcher(
        argument: IrExpression,
        declared: IrClass,
        stub: StubBlock,
        allAny: Boolean = false,
    ): IrExpression? =
        when {
            argument.isIndependentOf(stub.block) -> if (allAny) irAny() else irEq(argument)
            argument.isBuiltinMatcher -> irBuiltinMatcher(argument as IrCall, declared, stub)
            argument.isHelperOf(stub.block) -> irHelperMatcher(argument, stub)
            argument.contains { it.returnsFrom(stub.block) } ->
                fail(argument, "An argument of the stubbed call must not return from the block")
            else -> fail(
                argument,
                "A matcher inside an expression is not supported: pass the matcher as a whole argument",
            )
        }

    private fun IrBlockBodyBuilder.irBuiltinMatcher(call: IrCall, declared: IrClass, stub: StubBlock): IrExpression? {
        val name = call.symbol.owner.name.asString()
        val type = call.typeArguments[0]!!
        val matcher = when (name) {
            in ANY_MATCHERS -> irAny()
            in COMPOSITE_MATCHERS -> irFactory(call) { irMatcher(it, declared, stub) }
            else -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(stub.block) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
        } ?: return null
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

    private fun IrBlockBodyBuilder.irHelperMatcher(helper: IrExpression, stub: StubBlock): IrExpression {
        val scope = stub.scope ?: irTemporary(
            irCallConstructor(symbols.registrationScope.primaryConstructor!!.symbol, emptyList()),
            irType = symbols.matcherScope.defaultType,
        ).also { stub.scope = it }
        val value = helper.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression =
                    if (expression.isScopeOf(stub.block)) irGet(scope) else expression
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

    private fun IrBuilder.irArrayGet(array: IrVariable, index: Int): IrExpression {
        val get = context.irBuiltIns.arrayClass.owner.functions.single { it.name.asString() == "get" }
        return irCall(get.symbol, context.irBuiltIns.anyNType).apply {
            arguments[0] = irGet(array)
            arguments[1] = irInt(index)
        }
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

private class StubBlock(val block: IrSimpleFunction, val allAny: Boolean) {
    var scope: IrVariable? = null
}

private class StubbedCall(
    val call: IrCall,
    val receiver: IrExpression,
    val arguments: List<IrExpression?>,
    val order: List<Int>,
)

private fun IrSimpleFunction.stubbedCall(): StubbedCall? {
    val statement = (body as? IrBlockBody)?.statements?.singleOrNull()
    var expression = if (statement is IrReturn) statement.value else statement
    while (expression is IrTypeOperatorCall && expression.operator in CASTS) expression = expression.argument
    var frozen = emptyList<IrVariable>()
    if (expression is IrBlock && expression.origin == IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL) {
        frozen = expression.statements.filterIsInstance<IrVariable>()
        expression = expression.statements.last() as? IrExpression
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

private const val ALL_ANY = "allAny"
private const val TYPED = "typed"
private const val SPREAD = "spread"
private val ANY_MATCHERS = setOf("any", "anyNullable", ALL_ANY)
private val COMPOSITE_MATCHERS = setOf("and", "or", "not")
private val CAPTURE_MATCHERS = setOf("capture", "captureNullable")
private val COMPARISON_MATCHERS = setOf("cmpEq", "more", "less", "range")
private val TYPED_MATCHERS =
    setOf("any", "anyNullable", "match", "matchNullable") + COMPARISON_MATCHERS + CAPTURE_MATCHERS
private val BUILTIN_MATCHERS =
    TYPED_MATCHERS + COMPOSITE_MATCHERS + setOf(ALL_ANY, "eq", "neq", "refEq", "nrefEq", "isNull", "ofType")
