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
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
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
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.SpecialNames

internal class StubBlockLowering(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val unsupported: (IrElement, String) -> Unit,
) {
    fun lower(every: IrCall, scope: Scope): IrExpression = stub(every, scope) ?: every

    private fun stub(every: IrCall, scope: Scope): IrExpression? {
        val function = every.symbol.owner.name
        val block = (every.arguments[0] as? IrFunctionExpression)?.function
            ?: return fail(every, "The block of '$function' must be a lambda literal")
        val stubbed = block.stubbedCall()
            ?: return fail(block, "The block of '$function' must be a single call of a mock member")
        val call = stubbed.call
        val member = call.symbol.owner
        if (member.isMethodOfAny()) return fail(call, "Stubbing '${member.name}' is not supported yet")
        if (!stubbed.receiver.isIndependentOf(block)) {
            return fail(stubbed.receiver, "The receiver of the stubbed call must not use matchers")
        }
        val parameters = member.nonDispatchParameters
        val declared = member.root.parameters
        val capturesDefaults = null in stubbed.arguments

        return IrBlockBuilder(context, scope, every.startOffset, every.endOffset, resultType = every.type).block {
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
                val matcher = irArgumentMatcher(argument, declared[parameter.indexInParameters], block) ?: return null
                matchers[index] = irTemporary(matcher)
            }
            val defaults = if (capturesDefaults) {
                call.dispatchReceiver = irGet(receiver)
                parameters.forEachIndexed { index, parameter ->
                    val argument = stubbed.arguments[index]
                    call.arguments[parameter.indexInParameters] =
                        argument?.let { values[index]?.let(::irGet) ?: irPlaceholder(it) }
                }
                irTemporary(irCaptureDefaults(state, key, call))
            } else {
                null
            }
            +irCall(symbols.stub.symbol, every.type, listOf(every.typeArguments[0]!!)).apply {
                arguments[0] = irGet(state)
                arguments[1] = irString(key)
                arguments[2] = irArrayOf(
                    symbols.matcher.typeWith(context.irBuiltIns.anyNType),
                    parameters.indices.map { index ->
                        matchers[index]?.let(::irGet)
                            ?: irEq(values[index]?.let(::irGet) ?: irArrayGet(defaults!!, index))
                    },
                )
            }
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun fail(element: IrElement, reason: String): Nothing? {
        unsupported(element, reason)
        return null
    }

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

    private fun IrBuilder.irArgumentMatcher(
        argument: IrExpression,
        declared: IrValueParameter,
        block: IrSimpleFunction,
    ): IrExpression? {
        if (argument !is IrVararg) return irMatcher(argument, declared.type.erasedUpperBound, block)
        val elementType = declared.varargElementType!!.erasedUpperBound
        val elements = argument.elements.map { element ->
            if (element is IrSpreadElement) {
                if (!element.expression.isIndependentOf(block)) {
                    return fail(element.expression, "A matcher cannot be spread into a vararg parameter")
                }
                IrSpreadElementImpl(
                    startOffset,
                    endOffset,
                    irCall(symbols.matcherFactory(SPREAD)).apply {
                        arguments[0] = irMatchers()
                        arguments[1] = element.expression
                    },
                )
            } else {
                irMatcher(element as IrExpression, elementType, block) ?: return null
            }
        }
        return irCallConstructor(symbols.arrayMatcher.primaryConstructor!!.symbol, emptyList()).apply {
            arguments[0] = irArrayOf(symbols.matcher.typeWith(context.irBuiltIns.anyNType), elements)
        }
    }

    private fun IrBuilder.irMatcher(argument: IrExpression, declared: IrClass, block: IrSimpleFunction): IrExpression? =
        when {
            argument is IrCall && argument.intrinsicKind == IntrinsicKind.MATCHER ->
                irBuiltinMatcher(argument, declared, block)
            argument.isIndependentOf(block) -> irEq(argument)
            else -> fail(
                argument,
                "A matcher must be a whole argument: matcher helpers and matchers inside expressions " +
                    "are not supported yet",
            )
        }

    private fun IrBuilder.irBuiltinMatcher(call: IrCall, declared: IrClass, block: IrSimpleFunction): IrExpression? {
        val name = call.symbol.owner.name.asString()
        val type = call.typeArguments[0]!!
        val matcher = when (name) {
            in ANY_MATCHERS -> irGetObjectValue(symbols.anyMatcher.defaultType, symbols.anyMatcher.symbol)
            in COMPOSITE_MATCHERS -> irFactory(call) { irMatcher(it, declared, block) }
            in VALUE_MATCHERS -> irFactory(call) { value ->
                value.takeIf { it.isIndependentOf(block) }
                    ?: fail(value, "An argument of the matcher '$name' must not use matchers")
            }
            OF_TYPE -> irReifiedFactory(name, type)
            else -> fail(call, "The matcher '$name' is not supported by this version of the lokki compiler plugin")
        } ?: return null
        val typed = name in TYPED_MATCHERS && type.isStrictSubtypeOf(declared)
        return if (typed) irReifiedFactory(TYPED, type, matcher) else matcher
    }

    private fun IrBuilder.irReifiedFactory(name: String, type: IrType, matcher: IrExpression? = null): IrExpression =
        irCall(symbols.matcherFactory(name)).apply {
            typeArguments[0] = type
            arguments[0] = irMatchers()
            if (matcher != null) arguments[1] = matcher
        }

    private inline fun IrBuilder.irFactory(call: IrCall, argument: (IrExpression) -> IrExpression?): IrExpression? {
        val function = call.symbol.owner
        return irCall(symbols.matcherFactory(function.name.asString())).apply {
            arguments[0] = irMatchers()
            function.nonDispatchParameters.forEachIndexed { index, parameter ->
                val passed = call.arguments[parameter.indexInParameters] ?: return@forEachIndexed
                arguments[index + 1] = argument(passed) ?: return null
            }
        }
    }

    private fun IrBuilder.irMatchers(): IrExpression =
        irGetObjectValue(symbols.matchers.defaultType, symbols.matchers.symbol)

    private fun IrBuilder.irStateOf(instance: IrExpression): IrExpression =
        irCall(symbols.state.getter!!).apply {
            arguments[0] = irCall(symbols.of).apply {
                val companion = symbols.of.parentAsClass
                arguments[0] = irGetObjectValue(companion.defaultType, companion.symbol)
                arguments[1] = instance
            }
        }

    private fun IrBlockBuilder.irCaptureDefaults(state: IrVariable, key: String, call: IrCall): IrExpression {
        val lambda = context.irFactory.buildFun {
            startOffset = this@irCaptureDefaults.startOffset
            endOffset = this@irCaptureDefaults.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = context.irBuiltIns.unitType
        }
        lambda.body = DeclarationIrBuilder(context, lambda.symbol, startOffset, endOffset).irBlockBody { +call }
        return irCall(symbols.captureDefaults).apply {
            arguments[0] = irGet(state)
            arguments[1] = irString(key)
            arguments[2] = IrFunctionExpressionImpl(
                startOffset,
                endOffset,
                context.irBuiltIns.functionN(0).typeWith(context.irBuiltIns.unitType),
                lambda,
                IrStatementOrigin.LAMBDA,
            )
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

    private fun IrBuilder.irPlaceholder(argument: IrExpression): IrExpression =
        when {
            argument is IrVararg -> IrVarargImpl(startOffset, endOffset, argument.type, argument.varargElementType)
            argument.type.isPrimitiveType() -> IrConstImpl.defaultValueForType(startOffset, endOffset, argument.type)
            else -> irNull(argument.type)
        }
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

private fun IrType.isStrictSubtypeOf(declared: IrClass): Boolean {
    if (classifierOrNull is IrTypeParameterSymbol) return true
    val type = erasedUpperBound
    return type != declared && type.isSubclassOf(declared)
}

private fun IrExpression.isIndependentOf(block: IrSimpleFunction): Boolean {
    var independent = true
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            if (independent) element.acceptChildrenVoid(this)
        }

        override fun visitGetValue(expression: IrGetValue) {
            if ((expression.symbol.owner as? IrValueParameter)?.parent == block) independent = false
        }

        override fun visitReturn(expression: IrReturn) {
            if (expression.returnTargetSymbol == block.symbol) independent = false
            super.visitReturn(expression)
        }
    })
    return independent
}

private val CASTS = setOf(
    IrTypeOperator.CAST,
    IrTypeOperator.IMPLICIT_CAST,
    IrTypeOperator.IMPLICIT_NOTNULL,
    IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
)

private val ANY_MATCHERS = setOf("any", "anyNullable")
private val COMPOSITE_MATCHERS = setOf("and", "or", "not")
private val VALUE_MATCHERS =
    setOf("eq", "neq", "refEq", "nrefEq", "isNull", "cmpEq", "more", "less", "range", "match", "matchNullable")
private val TYPED_MATCHERS = ANY_MATCHERS + setOf("cmpEq", "more", "less", "range", "match", "matchNullable")
private const val OF_TYPE = "ofType"
private const val TYPED = "typed"
private const val SPREAD = "spread"
