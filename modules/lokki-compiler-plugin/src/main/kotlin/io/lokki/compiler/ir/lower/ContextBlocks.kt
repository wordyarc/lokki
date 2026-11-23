package io.lokki.compiler.ir.lower

import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.contains
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.replaceBlockCalls
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
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
        expression.replaceBlockCalls { call, block ->
            if (call.intrinsicKind == IntrinsicKind.WITH_CONTEXT) call.contextCall(block) else null
        }
        return super.visitCall(expression)
    }

    private fun IrCall.contextCall(block: IrSimpleFunction): IrExpression? {
        val scope = block.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver } ?: return null
        val receiver = symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
        if ((arguments[receiver] as? IrGetValue)?.symbol != scope.symbol) return null
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
                    return builder.irInvoke(
                        symbols[CoreFunction.ANY],
                        receiver = builder.irGet(scope),
                        type = expression.type,
                        typeArguments = listOf(expression.type),
                    )
                }
            },
            null,
        ).also { it.patchDeclarationParents(block) }
    }
}
