package io.lokki.compiler.ir.lower

import io.lokki.compiler.BLOCK_KINDS
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.contains
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.rewrap
import io.lokki.compiler.ir.wrapped
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class ContextBlocks(lokki: LokkiIrContext) : IrElementTransformerVoid() {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.intrinsicKind in BLOCK_KINDS) {
            val block = (expression.arguments.lastOrNull() as? IrFunctionExpression)?.function
            if (block != null) (block.body as? IrBlockBody)?.statements?.replaceAll { it.withoutContext(block) }
        }
        return super.visitCall(expression)
    }

    private fun IrStatement.withoutContext(block: IrSimpleFunction): IrStatement =
        if (this is IrExpression) withoutContext(block) else this

    private fun IrExpression.withoutContext(block: IrSimpleFunction): IrExpression {
        val wrapped = wrapped
        when {
            this is IrReturn -> value = value.withoutContext(block)
            wrapped != null -> rewrap(wrapped.withoutContext(block))
            this is IrCall && intrinsicKind == IntrinsicKind.WITH_CONTEXT -> return contextCall(block) ?: this
        }
        return this
    }

    private fun IrCall.contextCall(block: IrSimpleFunction): IrExpression? {
        val scope = block.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver } ?: return null
        val receiver = symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
        if ((arguments[receiver.indexInParameters] as? IrGetValue)?.symbol != scope.symbol) return null
        val lambda = (arguments.last() as? IrFunctionExpression)?.function ?: return null
        val statement = (lambda.body as? IrBlockBody)?.statements?.singleOrNull() ?: return null
        val value = when {
            statement is IrReturn && statement.returnTargetSymbol == lambda.symbol -> statement.value
            else -> statement as? IrExpression ?: return null
        }
        if (value.contains { it is IrReturn && it.returnTargetSymbol == lambda.symbol }) return null
        val contexts = lambda.parameters.filter { it.kind == IrParameterKind.Context }.map { it.symbol }.toSet()
        val builder = DeclarationIrBuilder(context, block.symbol, startOffset, endOffset)
        return value.transform(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    if (expression.symbol !in contexts) return expression
                    val any = symbols[CoreFunction.ANY].symbol
                    return builder.irCall(any, expression.type, listOf(expression.type)).apply {
                        arguments[0] = builder.irGet(scope)
                    }
                }
            },
            null,
        ).also { it.patchDeclarationParents(block) }
    }
}
