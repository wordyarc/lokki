package io.lokki.compiler.ir.lower

import io.lokki.compiler.ir.IntrinsicKind
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal class DslLowering(
    context: IrPluginContext,
    symbols: LokkiSymbols,
    private val file: IrFile,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoidWithContext() {
    private val stubBlocks = StubBlockLowering(context, symbols)

    override fun visitCall(expression: IrCall): IrExpression {
        super.visitCall(expression)
        return when (expression.intrinsicKind) {
            IntrinsicKind.MOCK -> expression.mockClassKey(file)?.let(mocks::get)?.let(expression::mockOf)
            IntrinsicKind.STUB_BLOCK -> currentScope?.let { stubBlocks.lower(expression, it.scope) }
            else -> null
        } ?: expression
    }
}

private fun IrCall.mockOf(mock: IrClass): IrExpression =
    IrConstructorCallImpl.fromSymbolOwner(startOffset, endOffset, mock.defaultType, mock.primaryConstructor!!.symbol)
