package io.lokki.compiler.ir.lower

import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.mockClassKey
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class MockCallLowering(
    private val file: IrFile,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoid() {
    override fun visitCall(expression: IrCall): IrExpression {
        val mock = expression.mockClassKey(file)?.let(mocks::get) ?: return super.visitCall(expression)
        return IrConstructorCallImpl.fromSymbolOwner(
            expression.startOffset,
            expression.endOffset,
            mock.defaultType,
            mock.primaryConstructor!!.symbol,
        )
    }
}
