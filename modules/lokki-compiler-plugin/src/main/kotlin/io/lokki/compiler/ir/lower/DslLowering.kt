package io.lokki.compiler.ir.lower

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.argument
import io.lokki.compiler.ir.collect.mockClassKey
import io.lokki.compiler.ir.collect.mockedTypes
import io.lokki.compiler.ir.collect.notMockableReason
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.collect.spiedInstance
import io.lokki.compiler.ir.collect.temporary
import io.lokki.compiler.ir.generate.DELEGATE_FLAG
import io.lokki.compiler.ir.generate.MockInstances
import io.lokki.compiler.ir.generate.RELAXED_FLAG
import io.lokki.compiler.ir.generate.RELAX_UNIT_FUN_FLAG
import io.lokki.compiler.ir.generate.SPY_FLAG
import io.lokki.compiler.ir.generate.replaceConstructorCall
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal class DslLowering(
    private val context: IrPluginContext,
    private val symbols: LokkiSymbols,
    private val file: IrFile,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoidWithContext() {
    private val instances = MockInstances(context, symbols)
    private val stubBlocks = StubBlockLowering(context, symbols, file, mocks, instances, ::unsupported)
    private val verifiedNoCalls = HashSet<IrCall>()

    override fun visitCall(expression: IrCall): IrExpression {
        val kind = expression.intrinsicKind
        if (kind == IntrinsicKind.VERIFY_BLOCK) verifiedNoCalls += stubBlocks.noCallsStatements(expression)
        super.visitCall(expression)
        return when (kind) {
            IntrinsicKind.MOCK, IntrinsicKind.SPY -> lowerMock(expression)
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
            IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, IntrinsicKind.RELAXED_MOCK,
            IntrinsicKind.ADDITIONAL_INTERFACE, null -> expression
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
        val spied = call.spiedInstance?.temporary
        val name = call.argument("name")?.temporary
        val nameAfterConstructor = spied != null && name != null && name.startOffset > spied.startOffset
        if (types.form is MockForm.Construction && nameAfterConstructor) {
            unsupported(call, "The name of a spy must precede its constructor call or be a constant")
            return call
        }
        val type = types.type
        val scope = currentScope!!.scope.scopeOwnerSymbol
        return DeclarationIrBuilder(context, scope, call.startOffset, call.endOffset).run {
            val flags = when {
                call.intrinsicKind == IntrinsicKind.MOCK -> irFlags(call)
                types.form == MockForm.Delegation -> irInt(SPY_FLAG or DELEGATE_FLAG)
                else -> irInt(SPY_FLAG)
            }
            val spec = irCallConstructor(symbols.mockSpec.primaryConstructor!!.symbol, emptyList()).apply {
                arguments[0] = call.argument("name") ?: irNull()
                arguments[1] = flags
            }
            val typeArguments = type.referencedTypeParameters.map { it.defaultType }
            val mockType = mock.typeWith(typeArguments)
            val instance = with(instances) {
                when (types.form) {
                    MockForm.Mock -> irNewMock(mock, types, spec)
                    MockForm.Delegation -> irDelegatingSpy(mock, types, call.spiedInstance!!, spec)
                    is MockForm.Construction -> {
                        val instance = call.spiedInstance
                        val temporary = instance?.temporary
                        val constructed = temporary?.initializer ?: instance
                        val spy = constructed?.replaceConstructorCall(mockType) { constructor ->
                            irConstruct(mock, typeArguments, constructor.arguments.filterNotNull() + spec)
                        } ?: irConstruct(mock, typeArguments, listOf(spec))
                        if (temporary == null) spy else instance.also { temporary.initializer = spy }
                    }
                }
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

    private fun unsupported(element: IrElement, reason: String) {
        context.diagnosticReporter.at(element, file).report(LokkiDiagnostics.LOKKI_UNSUPPORTED, reason)
    }
}

private val REFERABLE = setOf(IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, null)
private val MOCK_FLAGS = listOf("relaxed" to RELAXED_FLAG, "relaxUnitFun" to RELAX_UNIT_FUN_FLAG)
