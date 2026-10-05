package io.lokki.compiler.ir.lower

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.collect.mockedType
import io.lokki.compiler.ir.collect.notMockableReason
import io.lokki.compiler.ir.generate.INIT
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal class DslLowering(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val file: IrFile,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoidWithContext() {
    private val stubBlocks = StubBlockLowering(context, symbols, ::unsupported)
    private val verifiedNoCalls = HashSet<IrCall>()

    override fun visitCall(expression: IrCall): IrExpression {
        val kind = expression.intrinsicKind
        if (kind == IntrinsicKind.VERIFY_BLOCK) verifiedNoCalls += stubBlocks.noCallsStatements(expression)
        super.visitCall(expression)
        return when (kind) {
            IntrinsicKind.MOCK -> lowerMock(expression)
            IntrinsicKind.STUB_BLOCK -> stubBlocks.lowerStub(expression, currentScope!!.scope)
            IntrinsicKind.VERIFY_BLOCK -> stubBlocks.lowerVerify(expression, currentScope!!.scope)
            IntrinsicKind.EXCLUDE_BLOCK -> stubBlocks.lowerExclusion(expression, currentScope!!.scope)
            IntrinsicKind.WAS_NOT_CALLED -> expression.also {
                if (it !in verifiedNoCalls) unsupported(it, "'wasNot Called' must be a statement of a verify block")
            }
            IntrinsicKind.UNKNOWN -> expression.also {
                unsupported(it, "'${it.symbol.owner.name}' requires a newer version of the lokki compiler plugin")
            }
            IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, null -> expression
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
        val key = call.mockClassKey(file)
        val mock = key?.let(mocks::get) ?: return call.also { unsupported(it, it.mockedType.notMockableReason(file)!!) }
        val scope = currentScope!!.scope.scopeOwnerSymbol
        return DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset).run {
            val spec = irCallConstructor(symbols.mockSpec.primaryConstructor!!.symbol, emptyList()).apply {
                arguments[0] = call.arguments[0] ?: irNull()
                arguments[1] = irInt(0)
            }
            val instance = if (key.mockedClass.isInterface) {
                irCallConstructor(mock.primaryConstructor!!.symbol, emptyList()).apply { arguments[0] = spec }
            } else {
                irAllocate(mock, spec)
            }
            val block = call.arguments[1] ?: return instance
            irCall(symbols.apply, call.type, listOf(call.type)).apply {
                arguments[0] = instance
                arguments[1] = block
            }
        }
    }

    private fun IrBuilderWithScope.irAllocate(mock: IrClass, spec: IrExpression): IrExpression = irBlock {
        val type = mock.defaultType
        val instance = irTemporary(
            irCall(symbols.allocate.symbol, type, listOf(type)).apply {
                arguments[0] = irGetObjectValue(symbols.instanceAllocator.defaultType, symbols.instanceAllocator.symbol)
                val javaClass = symbols.javaClass.returnType.classOrFail.typeWith(type)
                arguments[1] = irCall(symbols.javaClass.symbol, javaClass, listOf(type)).apply {
                    arguments[0] = IrClassReferenceImpl(
                        startOffset,
                        endOffset,
                        context.irBuiltIns.kClassClass.typeWith(type),
                        mock.symbol,
                        type,
                    )
                }
            }
        )
        +irCall(mock.functions.single { it.name == INIT }).apply {
            arguments[0] = irGet(instance)
            arguments[1] = spec
        }
        +irGet(instance)
    }

    private fun unsupported(element: IrElement, reason: String) {
        context.diagnosticReporter.at(element, file).report(LokkiDiagnostics.LOKKI_UNSUPPORTED, reason)
    }
}

private val REFERABLE = setOf(IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, null)
