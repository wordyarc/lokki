package io.lokki.compiler.ir.lower

import io.lokki.compiler.ir.IntrinsicKind
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.memberKeys
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.SpecialNames

internal class StubBlockLowering(private val context: IrPluginContext, private val symbols: LokkiSymbols) {
    fun lower(every: IrCall, scope: Scope): IrExpression? {
        val block = (every.arguments[0] as? IrFunctionExpression)?.function ?: return null
        val call = block.stubbedCall() ?: return null
        val parameters = call.symbol.owner.nonDispatchParameters
        val passed = parameters.map { call.arguments[it.indexInParameters] }
        if (!isSupported(block, call, parameters, passed)) return null

        return IrBlockBuilder(context, scope, every.startOffset, every.endOffset, resultType = every.type).block {
            val receiver = irTemporary(call.dispatchReceiver!!)
            val state = irTemporary(irStateOf(irGet(receiver)))
            val key = call.symbol.owner.memberKeys.first()
            val values = passed.map { argument -> argument?.takeUnless { it.isMatcher }?.let { irTemporary(it) } }
            val defaults = if (null in passed) {
                call.dispatchReceiver = irGet(receiver)
                parameters.forEachIndexed { index, parameter ->
                    val argument = passed[index] ?: return@forEachIndexed
                    call.arguments[parameter.indexInParameters] = values[index]?.let(::irGet) ?: irPlaceholder(argument.type)
                }
                irTemporary(irCaptureDefaults(state, key, call))
            } else {
                null
            }
            val matchers = passed.mapIndexed { index, argument ->
                when {
                    argument == null -> irEq(irArrayGet(defaults!!, index))
                    argument.isMatcher -> irGetObjectValue(symbols.anyMatcher.defaultType, symbols.anyMatcher.symbol)
                    else -> irEq(irGet(values[index]!!))
                }
            }
            +irCall(symbols.stub.symbol, every.type, listOf(every.typeArguments[0]!!)).apply {
                arguments[0] = irGet(state)
                arguments[1] = irString(key)
                arguments[2] = irArrayOf(symbols.matcher.typeWith(context.irBuiltIns.anyNType), matchers)
            }
        }.patchDeclarationParents(scope.getLocalDeclarationParent())
    }

    private fun isSupported(
        block: IrSimpleFunction,
        call: IrCall,
        parameters: List<IrValueParameter>,
        passed: List<IrExpression?>,
    ): Boolean {
        val capturesDefaults = null in passed
        return call.dispatchReceiver!!.isIndependentOf(block) && parameters.indices.all { index ->
            val argument = passed[index]
            when {
                argument == null -> parameters[index].varargElementType == null
                !argument.isMatcher -> argument.isIndependentOf(block)
                else -> argument.isAny && !(capturesDefaults && argument.type.classOrNull?.owner?.isValue == true)
            }
        }
    }

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

    private fun IrBuilder.irPlaceholder(type: IrType): IrExpression =
        if (type.isPrimitiveType()) IrConstImpl.defaultValueForType(startOffset, endOffset, type) else irNull(type)
}

private fun IrSimpleFunction.stubbedCall(): IrCall? {
    val statement = (body as? IrBlockBody)?.statements?.singleOrNull()
    var expression = if (statement is IrReturn) statement.value else statement
    while (expression is IrTypeOperatorCall && expression.operator in CASTS) expression = expression.argument
    return (expression as? IrCall)?.takeIf { it.dispatchReceiver != null }
}

private val IrExpression.isMatcher: Boolean
    get() = this is IrCall && intrinsicKind == IntrinsicKind.MATCHER

private val IrExpression.isAny: Boolean
    get() = this is IrCall && symbol.owner.name.asString() == "any"

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
