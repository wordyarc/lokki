package io.lokki.compiler.ir.lower

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.ObjectRedirect
import io.lokki.compiler.ir.Redirect
import io.lokki.compiler.ir.Redirects
import io.lokki.compiler.ir.StaticRedirect
import io.lokki.compiler.ir.collect.MockedTypes
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irVararg
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.staticReferences
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.expressions.IrCallableReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.SpecialNames
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
    private val redirects: Redirects,
) : IrElementTransformerVoidWithContext() {
    private val instances = MockInstances(context, symbols)
    private val stubBlocks = StubBlockLowering(context, symbols, file, mocks, instances, redirects, ::unsupported)
    private val verifiedNoCalls = HashSet<IrCall>()

    override fun visitCall(expression: IrCall): IrExpression {
        val kind = expression.intrinsicKind
        if (kind == IntrinsicKind.VERIFY_BLOCK) verifiedNoCalls += stubBlocks.noCallsStatements(expression)
        super.visitCall(expression)
        return when (kind) {
            IntrinsicKind.MOCK, IntrinsicKind.SPY -> lowerMock(expression)
            IntrinsicKind.MOCK_OBJECT -> lowerMockObject(expression)
            IntrinsicKind.MOCK_STATIC -> lowerMockStatic(expression)
            IntrinsicKind.STATIC_MOCKS -> lowerStaticMocks(expression)
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
            IntrinsicKind.ADDITIONAL_INTERFACE, IntrinsicKind.INJECT_MOCKS, null -> expression
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

    private fun lowerMockObject(call: IrCall): IrExpression {
        val objects = call.mockedObjects
            ?: return call.also { unsupported(it, "Pass objects to 'mockObject' directly, not as a spread array") }
        val redirects = objects.map { argument ->
            redirects.of(argument) ?: return call.also {
                unsupported(argument, "'${argument.type.render()}' is not an object with interception points")
            }
        }
        val scope = currentScope!!.scope
        val block = call.argument("block")
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, call.startOffset, call.endOffset).run {
            val type = symbols.redirect.defaultType
            val array = irArrayOf(type, objects.zip(redirects).map { (target, redirect) -> irRedirect(target, redirect) })
            irCall(if (block == null) symbols.mockObject else symbols.mockObjectWithBlock).apply {
                arguments[0] = irGetObjectValue(symbols.redirects.defaultType, symbols.redirects.symbol)
                arguments[1] = array
                if (block != null) arguments[2] = block
            }
        }.also { it.patchDeclarationParents(scope.getLocalDeclarationParent()) }
    }

    private fun lowerMockStatic(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        val scope = currentScope!!.scope
        val block = call.argument("block")
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, call.startOffset, call.endOffset).run {
            val array = irArrayOf(symbols.redirect.defaultType, statics.map { irRedirect(irString(it.key), it) })
            irCall(if (block == null) symbols.mockStatic else symbols.mockStaticWithBlock).apply {
                arguments[0] = irGetObjectValue(symbols.redirects.defaultType, symbols.redirects.symbol)
                arguments[1] = array
                if (block != null) arguments[2] = block
            }
        }.also { it.patchDeclarationParents(scope.getLocalDeclarationParent()) }
    }

    private fun lowerStaticMocks(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        val scope = currentScope!!.scope
        return DeclarationIrBuilder(context, scope.scopeOwnerSymbol, call.startOffset, call.endOffset).run {
            val any = context.irBuiltIns.anyType
            when (call.symbol.owner.name.asString()) {
                UNMOCK_STATIC -> irCall(symbols.unmock).apply {
                    arguments[0] = irGetObjectValue(symbols.redirects.defaultType, symbols.redirects.symbol)
                    arguments[1] = irArrayOf(any, statics.map { irString(it.key) })
                }
                CLEAR_STATIC_MOCK -> irCall(symbols.clearMocks).apply {
                    val mocks = statics.map { irStaticMock(it) }
                    arguments[0] = mocks.first()
                    arguments[1] = irVararg(any, mocks.drop(1))
                }
                else -> irCall(symbols.confirmVerified).apply {
                    arguments[0] = irVararg(any, statics.map { irStaticMock(it) })
                    arguments[1] = call.argument("clear")
                }
            }
        }
    }

    private fun IrCall.staticRedirects(): List<StaticRedirect>? {
        val name = symbol.owner.name
        val references = staticReferences
            ?: return null.also { unsupported(this, "Pass references to '$name' directly, not as a spread array") }
        return references.map { reference ->
            redirects.static(reference) ?: return null.also {
                val target = ((reference as? IrCallableReference<*>)?.symbol?.owner as? IrDeclarationWithName)?.name
                val reason = when (target) {
                    null -> "An argument of '$name' must be an unbound reference like '::f'"
                    else -> "'$target' is not an unbound reference to a top-level function or property with " +
                        "interception points"
                }
                unsupported(reference, reason)
            }
        }
    }

    private fun IrBuilderWithScope.irStaticMock(redirect: StaticRedirect): IrExpression =
        irCall(symbols.staticRedirectOf).apply {
            arguments[0] = irGetObjectValue(symbols.redirects.defaultType, symbols.redirects.symbol)
            arguments[1] = irString(redirect.key)
            arguments[2] = irString(redirect.name)
        }

    private fun IrBuilderWithScope.irRedirect(target: IrExpression, redirect: Redirect): IrExpression {
        val types = MockedTypes(redirect.redirect.defaultType, emptyList(), MockForm.Mock)
        val mock = mocks.getValue(types.mockClassKey(file))
        val anyN = context.irBuiltIns.anyNType
        val unit = context.irBuiltIns.unitType
        val install = context.irFactory.buildFun {
            startOffset = this@irRedirect.startOffset
            endOffset = this@irRedirect.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = unit
        }
        val value = install.addValueParameter("redirect", anyN)
        install.body = DeclarationIrBuilder(context, install.symbol, startOffset, endOffset).irBlockBody {
            +irCall(redirect.property.setter!!).apply {
                val owner = (redirect as? ObjectRedirect)?.target?.let { irGetObjectValue(it.defaultType, it.symbol) }
                val installed = irAs(irGet(value), redirect.redirect.defaultType.makeNullable())
                (listOfNotNull(owner) + installed).forEachIndexed { index, argument -> arguments[index] = argument }
            }
        }
        val installType = context.irBuiltIns.functionN(1).typeWith(anyN, unit)
        return irCallConstructor(symbols.redirect.primaryConstructor!!.symbol, emptyList()).apply {
            arguments[0] = target
            arguments[1] = irString(redirect.name)
            arguments[2] = with(instances) { irFactory(mock, types) }
            arguments[3] = IrFunctionExpressionImpl(startOffset, endOffset, installType, install, IrStatementOrigin.LAMBDA)
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
private const val UNMOCK_STATIC = "unmockStatic"
private const val CLEAR_STATIC_MOCK = "clearStaticMock"
private val MOCK_FLAGS = listOf("relaxed" to RELAXED_FLAG, "relaxUnitFun" to RELAX_UNIT_FUN_FLAG)
