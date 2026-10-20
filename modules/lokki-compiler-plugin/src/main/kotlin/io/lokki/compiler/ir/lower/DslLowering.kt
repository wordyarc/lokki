package io.lokki.compiler.ir.lower

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.collect.mockedClass
import io.lokki.compiler.ir.collect.mockedTypes
import io.lokki.compiler.ir.collect.notMockableReason
import io.lokki.compiler.ir.collect.referencedTypeParameters
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
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
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
            IntrinsicKind.AWAITS -> stubBlocks.lowerAwaits(expression, currentScope!!.scope)
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
        val types = call.mockedTypes(file)
        val mock = types?.let { mocks[it.mockClassKey(file)] }
            ?: return call.also { unsupported(it, it.notMockableReason(file)!!) }
        val type = types.type
        val scope = currentScope!!.scope.scopeOwnerSymbol
        return DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset).run {
            val spec = irCallConstructor(symbols.mockSpec.primaryConstructor!!.symbol, emptyList()).apply {
                arguments[0] = call.argument("name") ?: irNull()
                arguments[1] = irFlags(call)
            }
            val typeArguments = type.referencedTypeParameters.map { it.defaultType }
            val mockType = mock.typeWith(typeArguments)
            val instance = if (type.mockedClass.isInterface) {
                irCallConstructor(mock.primaryConstructor!!.symbol, typeArguments).apply {
                    this.type = mockType
                    arguments[0] = spec
                }
            } else {
                irAllocate(mockType, spec)
            }
            val block = call.argument("block") ?: return instance
            irCall(symbols.apply, call.type, listOf(call.type)).apply {
                arguments[0] = instance
                arguments[1] = block
            }
        }
    }

    private fun IrBuilderWithScope.irFlags(call: IrCall): IrExpression {
        var constant = 0
        val conditional = ArrayList<IrExpression>()
        for ((name, flag) in MOCK_FLAGS) {
            val argument = call.argument(name) ?: continue
            when ((argument as? IrConst)?.value) {
                true -> constant = constant or flag
                false -> {}
                else -> conditional += irIfThenElse(context.irBuiltIns.intType, argument, irInt(flag), irInt(0))
            }
        }
        val flags = listOfNotNull(irInt(constant).takeIf { constant != 0 || conditional.isEmpty() }) + conditional
        return flags.reduce { left, right -> irCallOp(symbols.intOr.symbol, context.irBuiltIns.intType, left, right) }
    }

    private fun IrCall.argument(name: String): IrExpression? =
        arguments[symbol.owner.parameters.single { it.name.asString() == name }.indexInParameters]

    private fun IrBuilderWithScope.irAllocate(type: IrSimpleType, spec: IrExpression): IrExpression = irBlock {
        val mock = type.classOrFail.owner
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
private val MOCK_FLAGS = listOf("relaxed" to 1, "relaxUnitFun" to 2)
