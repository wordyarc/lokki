package io.lokki.compiler.ir.lower

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.VERIFY_BLOCKS
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.Redirect
import io.lokki.compiler.ir.StaticRedirect
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.MockSites
import io.lokki.compiler.ir.collect.spiedInstance
import io.lokki.compiler.ir.generate.irConstruct
import io.lokki.compiler.ir.generate.irDelegatingSpy
import io.lokki.compiler.ir.generate.irFactory
import io.lokki.compiler.ir.generate.irNewMock
import io.lokki.compiler.ir.generate.irSpec
import io.lokki.compiler.ir.generate.replaceConstructorCall
import io.lokki.compiler.ir.generate.spyFlags
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.irVararg
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.referencedDeclaration
import io.lokki.compiler.ir.staticReferences
import io.lokki.compiler.ir.temporary
import io.lokki.compiler.ir.toLambda
import io.lokki.compiler.isReferable
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.render

internal class DslLowering(
    lokki: LokkiIrContext,
    private val file: FileContext,
    private val sites: MockSites,
    private val mocks: Map<MockClassKey, IrClass>,
) : IrElementTransformerVoidWithContext() {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val stubBlocks = StubBlockLowering(lokki, file, sites, mocks)

    override fun visitCall(expression: IrCall): IrExpression {
        val kind = expression.intrinsicKind
        super.visitCall(expression)
        return when (kind) {
            null, IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, IntrinsicKind.RELAXED_MOCK,
            IntrinsicKind.ADDITIONAL_INTERFACE, IntrinsicKind.INJECT_MOCKS -> expression
            IntrinsicKind.MOCK, IntrinsicKind.SPY -> lowerMock(expression, kind)
            IntrinsicKind.MOCK_OBJECT -> lowerMockObject(expression)
            IntrinsicKind.MOCK_STATIC -> lowerMockStatic(expression)
            IntrinsicKind.UNMOCK_STATIC -> lowerUnmockStatic(expression)
            IntrinsicKind.CLEAR_STATIC_MOCK -> lowerClearStaticMock(expression)
            IntrinsicKind.CONFIRM_VERIFIED_STATIC -> lowerConfirmVerifiedStatic(expression)
            in STUB_BLOCKS -> stubBlocks.lowerStub(expression, kind, currentScope!!.scope)
            in VERIFY_BLOCKS -> stubBlocks.lowerVerify(expression, kind, currentScope!!.scope)
            IntrinsicKind.EXCLUDE_BLOCK -> stubBlocks.lowerExclusion(expression, currentScope!!.scope)
            IntrinsicKind.AWAITS -> stubBlocks.lowerAwaits(expression, currentScope!!.scope)
            IntrinsicKind.WAS_NOT_CALLED -> expression.also {
                if (it !in sites.blockStatementCalls) {
                    unsupported(it, "'wasNot Called' must be a statement of a verify block")
                }
            }
            IntrinsicKind.WITH_CONTEXT -> expression.also {
                if (it !in sites.blockStatementCalls) unsupported(
                    it,
                    "'withContext' must be a statement of a stub, verify or exclusion block, " +
                        "and its lambda must be a single call of a mock member",
                )
            }
            else -> expression.also {
                unsupported(it, "'${it.symbol.owner.name}' requires a newer version of the lokki compiler plugin")
            }
        }
    }

    override fun visitFunctionNew(declaration: IrFunction): IrStatement =
        when (declaration.origin) {
            IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE -> declaration
            else -> super.visitFunctionNew(declaration)
        }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression =
        if (expression.checkReferable()) super.visitFunctionReference(expression) else expression

    override fun visitRichFunctionReference(expression: IrRichFunctionReference): IrExpression =
        if (expression.checkReferable()) super.visitRichFunctionReference(expression) else expression

    private fun IrExpression.checkReferable(): Boolean {
        val function = referencedDeclaration as? IrFunction ?: return true
        if (function.intrinsicKind.isReferable) return true
        unsupported(this, "'${function.name}' must be called directly: a reference to it is not supported")
        return false
    }

    private fun lowerMock(call: IrCall, kind: IntrinsicKind): IrExpression {
        val site = sites.calls.getValue(call) as? MockSite.Mocked ?: return call
        val types = site.types
        val mock = mocks.getValue(site.key)
        val spied = call.spiedInstance?.temporary
        val name = call.argument(ParameterNames.NAME)?.temporary
        val nameAfterConstructor = spied != null && name != null && name.startOffset > spied.startOffset
        if (types.form is MockForm.Construction && nameAfterConstructor) {
            unsupported(call, "The name of a spy must precede its constructor call or be a constant")
            return call
        }
        return builder(call).run {
            val flags = if (kind == IntrinsicKind.MOCK) irFlags(call) else irInt(types.form.spyFlags)
            val spec = irSpec(symbols, call.argument(ParameterNames.NAME) ?: irNull(), flags)
            val typeArguments = types.typeArguments
            val mockType = mock.typeWith(typeArguments)
            val instance = when (types.form) {
                MockForm.Mock -> irNewMock(symbols, mock, types, spec)
                MockForm.Delegation -> irDelegatingSpy(symbols, mock, types, call.spiedInstance!!, spec)
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
            val block = call.argument(ParameterNames.BLOCK) ?: return instance
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
            file.redirects.of(argument) ?: return call.also {
                unsupported(argument, "'${argument.type.render()}' is not an object with interception points")
            }
        }
        val block = call.argument(ParameterNames.BLOCK)
        return builder(call).run {
            val installed = objects.zip(redirects).map { (target, redirect) -> irRedirect(target, redirect) }
            irInstall(CoreFunction.MOCK_OBJECT, CoreFunction.MOCK_OBJECT_WITH_BLOCK, installed, block)
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun lowerMockStatic(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        val block = call.argument(ParameterNames.BLOCK)
        return builder(call).run {
            val installed = statics.map { irRedirect(irString(it.key), it) }
            irInstall(CoreFunction.MOCK_STATIC, CoreFunction.MOCK_STATIC_WITH_BLOCK, installed, block)
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun lowerUnmockStatic(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        return builder(call).run {
            val keys = irArrayOf(context.irBuiltIns.anyType, statics.map { irString(it.key) })
            irInvoke(symbols[CoreFunction.UNMOCK], keys)
        }
    }

    private fun lowerClearStaticMock(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        return builder(call).run {
            irCall(symbols.clearMocks).apply {
                val mocks = statics.map { irStaticMock(it) }
                arguments[0] = mocks.first()
                arguments[1] = irVararg(context.irBuiltIns.anyType, mocks.drop(1))
            }
        }
    }

    private fun lowerConfirmVerifiedStatic(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        return builder(call).run {
            irCall(symbols.confirmVerified).apply {
                arguments[0] = irVararg(context.irBuiltIns.anyType, statics.map { irStaticMock(it) })
                arguments[1] = call.argument(ParameterNames.CLEAR)
            }
        }
    }

    private fun IrCall.staticRedirects(): List<StaticRedirect>? {
        val name = symbol.owner.name
        val references = staticReferences
            ?: return null.also { unsupported(this, "Pass references to '$name' directly, not as a spread array") }
        return references.map { reference ->
            file.redirects.static(reference) ?: return null.also {
                val target = reference.referencedDeclaration?.name
                val reason = when (target) {
                    null -> "An argument of '$name' must be an unbound reference like '::f'"
                    else -> "'$target' is not an unbound reference to a top-level function or property with " +
                        "interception points"
                }
                unsupported(reference, reason)
            }
        }
    }

    private fun IrBuilderWithScope.irInstall(
        install: CoreFunction,
        installWithBlock: CoreFunction,
        redirects: List<IrExpression>,
        block: IrExpression?,
    ): IrExpression {
        val installed = irArrayOf(symbols[CoreClass.REDIRECT].defaultType, redirects)
        return when (block) {
            null -> irInvoke(symbols[install], installed)
            else -> irInvoke(symbols[installWithBlock], installed, block)
        }
    }

    private fun IrBuilderWithScope.irStaticMock(redirect: StaticRedirect): IrExpression =
        irInvoke(symbols[CoreFunction.STATIC_REDIRECT_OF], irString(redirect.key), irString(redirect.name))

    private fun IrBuilderWithScope.irRedirect(target: IrExpression, redirect: Redirect): IrExpression {
        val site = redirect.site
        val mock = mocks.getValue(site.key)
        val install = context.buildLambda(startOffset, endOffset, context.irBuiltIns.unitType)
        val value = install.addValueParameter("redirect", context.irBuiltIns.anyNType)
        install.body = DeclarationIrBuilder(context, install.symbol, startOffset, endOffset).irBlockBody {
            +irInvoke(redirect.property.setter!!, irAs(irGet(value), redirect.redirect.defaultType.makeNullable()))
        }
        return irInvoke(
            symbols[CoreConstructor.REDIRECT],
            target,
            irString(redirect.name),
            irFactory(symbols, mock, site.types),
            install.toLambda(context.irBuiltIns),
        )
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

    private fun builder(call: IrCall): DeclarationIrBuilder =
        DeclarationIrBuilder(context, currentScope!!.scope.scopeOwnerSymbol, call.startOffset, call.endOffset)

    private fun unsupported(element: IrElement, reason: String) {
        file.diagnostics.unsupported(element, reason)
    }
}

private val MOCK_FLAGS = listOf(
    ParameterNames.RELAXED to BuildConfig.FLAG_RELAXED,
    ParameterNames.RELAX_UNIT_FUN to BuildConfig.FLAG_RELAX_UNIT_FUN,
)
