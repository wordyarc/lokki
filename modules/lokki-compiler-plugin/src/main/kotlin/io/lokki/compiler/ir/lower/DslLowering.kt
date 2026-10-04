package io.lokki.compiler.ir.lower

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.IntrinsicKind
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.collect.mockedType
import io.lokki.compiler.ir.collect.notMockableReason
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal class DslLowering(
    private val context: IrPluginContext,
    symbols: LokkiSymbols,
    private val file: IrFile,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoidWithContext() {
    private val stubBlocks = StubBlockLowering(context, symbols, ::unsupported)

    override fun visitCall(expression: IrCall): IrExpression {
        super.visitCall(expression)
        return when (expression.intrinsicKind) {
            IntrinsicKind.MOCK -> lowerMock(expression)
            IntrinsicKind.STUB_BLOCK -> stubBlocks.lower(expression, currentScope!!.scope)
            IntrinsicKind.UNKNOWN -> expression.also {
                unsupported(it, "'${it.symbol.owner.name}' requires a newer version of the lokki compiler plugin")
            }
            IntrinsicKind.MATCHER, null -> expression
        }
    }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
        val function = expression.symbol.owner
        if (function.intrinsicKind !in REFERABLE) {
            unsupported(expression, "'${function.name}' must be called directly: a reference to it is not supported")
        }
        return super.visitFunctionReference(expression)
    }

    private fun lowerMock(call: IrCall): IrExpression {
        val mock = call.mockClassKey(file)?.let(mocks::get)
            ?: return call.also { unsupported(it, it.mockedType.notMockableReason!!) }
        return IrConstructorCallImpl.fromSymbolOwner(
            call.startOffset,
            call.endOffset,
            mock.defaultType,
            mock.primaryConstructor!!.symbol,
        )
    }

    private fun unsupported(element: IrElement, reason: String) {
        context.diagnosticReporter.at(element, file).report(LokkiDiagnostics.LOKKI_UNSUPPORTED, reason)
    }
}

private val REFERABLE = setOf(IntrinsicKind.MATCHER, null)
