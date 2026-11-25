package io.lokki.compiler.ir.lower

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.VERIFY_BLOCKS
import io.lokki.compiler.ir.ConstructorRedirect
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.ObjectRedirect
import io.lokki.compiler.ir.Redirect
import io.lokki.compiler.ir.StaticRedirect
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.constructorClasses
import io.lokki.compiler.ir.fileClassName
import io.lokki.compiler.ir.locatingInternalErrors
import io.lokki.compiler.ir.staticClassArguments
import io.lokki.compiler.ir.staticFileArguments
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.MockSites
import io.lokki.compiler.ir.collect.spiedInstance
import io.lokki.compiler.ir.generate.ConstructedClasses
import io.lokki.compiler.ir.generate.MOCK_FLAGS
import io.lokki.compiler.ir.generate.RECORD_PRIVATE_FLAG
import io.lokki.compiler.ir.generate.irFactory
import io.lokki.compiler.ir.generate.irConstruct
import io.lokki.compiler.ir.generate.irDelegatingSpy
import io.lokki.compiler.ir.generate.irNewMock
import io.lokki.compiler.ir.generate.irRedirect
import io.lokki.compiler.ir.generate.irSpec
import io.lokki.compiler.ir.generate.replaceConstructorCall
import io.lokki.compiler.ir.generate.spyFlags
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.isConfigurationOfInstance
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
import org.jetbrains.kotlin.ir.builders.IrStatementsBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallOp
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
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
    private val constructedClasses = ConstructedClasses(lokki).generate(file, sites.constructed, mocks)

    override fun visitCall(expression: IrCall): IrExpression {
        val kind = expression.intrinsicKind
        super.visitCall(expression)
        val lowered = file.ir.locatingInternalErrors(expression) { lowerIntrinsic(expression, kind) }
        val reified = sites.reifiedCalls[expression] ?: return lowered
        return builder(expression).irBlock(resultType = lowered.type) {
            val factory = context.irBuiltIns.functionN(1)
                .typeWith(symbols[CoreClass.MOCK_SPEC].defaultType, context.irBuiltIns.anyType)
            val call = irTemporary(
                irInvoke(
                    symbols[CoreFunction.REIFIED_ENTER],
                    irArrayOf(symbols.typeOf.returnType, reified.map { irTypeOf(it.type) }),
                    irArrayOf(factory, reified.map { irFactory(symbols, mocks.getValue(it.site.key), it.site.types) }),
                )
            )
            +irTry(lowered.type, lowered, emptyList(), irInvoke(symbols[CoreFunction.REIFIED_EXIT], irGet(call)))
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun IrBuilderWithScope.irTypeOf(type: IrType): IrExpression =
        irCall(symbols.typeOf.symbol, symbols.typeOf.returnType, listOf(type))

    private fun lowerIntrinsic(expression: IrCall, kind: IntrinsicKind?): IrExpression =
        when (kind) {
            null, IntrinsicKind.MATCHER, IntrinsicKind.ANSWER, IntrinsicKind.RELAXED_MOCK,
            IntrinsicKind.ADDITIONAL_INTERFACE, IntrinsicKind.INJECT_MOCKS, IntrinsicKind.CONSTRUCTED,
            IntrinsicKind.DYNAMIC_MEMBER -> expression
            IntrinsicKind.DYNAMIC_CALL -> expression.also {
                if (it !in file.failedDynamicCalls) {
                    unsupported(it, "A dynamic call must be a whole statement of a stub, verify or exclusion block")
                }
            }
            IntrinsicKind.MOCK_CONSTRUCTOR -> lowerMockConstructor(expression)
            IntrinsicKind.UNMOCK_CONSTRUCTOR -> lowerUnmockConstructor(expression)
            IntrinsicKind.CLEAR_CONSTRUCTOR_MOCK -> lowerClearConstructorMock(expression)
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
        val found = sites.calls.getValue(call)
        if (found === MockSite.Reified) return lowerReifiedMock(call)
        val site = found as? MockSite.Mocked ?: return call
        call.argument(ParameterNames.MOCKED_CLASS)?.takeIf { it !is IrClassReference }?.let {
            unsupported(it, "'mockClass' needs a class literal such as 'Car::class'")
            return call
        }
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
            val flags = when (kind) {
                IntrinsicKind.MOCK -> irFlags(call, 0, MOCK_FLAGS)
                else -> irFlags(call, types.form.spyFlags, listOf(RECORD_PRIVATE_FLAG))
            }
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
                    val created = constructed?.replaceConstructorCall(mockType) { constructor ->
                        irConstruct(mock, typeArguments, constructor.arguments.filterNotNull() + spec)
                    } ?: irConstruct(mock, typeArguments, listOf(spec))
                    val spy = if (created.isConfigurationOfInstance) irWithoutRecords(created) else created
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

    private fun lowerReifiedMock(call: IrCall): IrExpression = builder(call).run {
        val type = call.typeArguments.single()!!
        val spec = irSpec(symbols, call.argument(ParameterNames.NAME) ?: irNull(), irFlags(call, 0, MOCK_FLAGS))
        val instance = irInvoke(
            symbols[CoreFunction.REIFIED_CREATE],
            irTypeOf(type),
            spec,
            type = type,
            typeArguments = listOf(type),
        )
        val block = call.argument(ParameterNames.BLOCK) ?: return instance
        irCall(symbols.apply, call.type, listOf(call.type)).apply {
            arguments[0] = instance
            arguments[1] = block
        }
    }

    private fun IrBuilderWithScope.irWithoutRecords(spy: IrExpression): IrExpression =
        irBlock(resultType = spy.type) {
            val configured = irTemporary(spy)
            val state = irInvoke(symbols[CoreFunction.STATE], receiver = irInvoke(symbols[CoreFunction.OF], irGet(configured)))
            +irInvoke(symbols[CoreFunction.FORGET_RECORDS], receiver = state)
            +irGet(configured)
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
        return builder(call).irBlock {
            val flags = irRecordPrivateCalls(call)
            val installed = objects.zip(redirects).map { (target, redirect) -> irRedirect(target, redirect, flags?.invoke()) }
            +irInstall(CoreFunction.MOCK_OBJECT, CoreFunction.MOCK_OBJECT_WITH_BLOCK, installed, block)
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun IrStatementsBuilder<*>.irRecordPrivateCalls(call: IrCall): (() -> IrExpression)? {
        val argument = call.argument(ParameterNames.RECORD_PRIVATE_CALLS) ?: return null
        val flag = BuildConfig.FLAG_RECORD_PRIVATE_CALLS
        if (argument is IrConst) return { irInt(if (argument.value == true) flag else 0) }
        val flags = irTemporary(irIfThenElse(context.irBuiltIns.intType, argument, irInt(flag), irInt(0)))
        return { irGet(flags) }
    }

    private fun IrCall.staticClassRedirects(): List<ObjectRedirect>? {
        val classes = staticClassArguments ?: return null
        return classes.map { element ->
            file.redirects.ofStaticClass(element) ?: return emptyList<ObjectRedirect>().also {
                unsupported(this, "An argument of '${symbol.owner.name}' must be a literal of an object or of a class " +
                    "with a companion object that has interception points")
            }
        }
    }

    private fun lowerMockStatic(call: IrCall): IrExpression {
        call.staticClassRedirects()?.let { redirects ->
            if (redirects.isEmpty()) return call
            val block = call.argument(ParameterNames.BLOCK)
            return builder(call).run {
                val installed = redirects.map { irRedirect(irGetObject(it.target), it) }
                irInstall(CoreFunction.MOCK_STATIC, CoreFunction.MOCK_STATIC_WITH_BLOCK, installed, block)
            }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
        }
        val statics = call.staticRedirects() ?: return call
        val block = call.argument(ParameterNames.BLOCK)
        return builder(call).run {
            val installed = statics.map { irRedirect(irString(it.key), it) }
            irInstall(CoreFunction.MOCK_STATIC, CoreFunction.MOCK_STATIC_WITH_BLOCK, installed, block)
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun lowerUnmockStatic(call: IrCall): IrExpression {
        call.staticClassRedirects()?.let { redirects ->
            if (redirects.isEmpty()) return call
            return builder(call).run {
                irInvoke(
                    symbols[CoreFunction.UNMOCK],
                    irArrayOf(context.irBuiltIns.anyType, redirects.map { irGetObject(it.target) }),
                )
            }
        }
        val statics = call.staticRedirects() ?: return call
        return builder(call).run {
            val keys = irArrayOf(context.irBuiltIns.anyType, statics.map { irString(it.key) })
            irInvoke(symbols[CoreFunction.UNMOCK], keys)
        }
    }

    private fun lowerClearStaticMock(call: IrCall): IrExpression {
        call.staticClassRedirects()?.let { redirects ->
            if (redirects.isEmpty()) return call
            return builder(call).run {
                val mocks = redirects.map {
                    irInvoke(symbols[CoreFunction.REDIRECT_OF], irGetObject(it.target), irString(it.name))
                }
                irInvoke(
                    symbols[CoreFunction.CLEAR_OBJECTS],
                    irArrayOf(context.irBuiltIns.anyType, mocks),
                    call.argument(ParameterNames.ANSWERS) ?: irTrue(),
                    call.argument(ParameterNames.RECORDED_CALLS) ?: irTrue(),
                    call.argument(ParameterNames.CHILD_MOCKS) ?: irTrue(),
                )
            }
        }
        val statics = call.staticRedirects() ?: return call
        return builder(call).run { irInvoke(symbols[CoreFunction.CLEAR_STATIC], irStaticMocks(statics)) }
    }

    private fun lowerConfirmVerifiedStatic(call: IrCall): IrExpression {
        val statics = call.staticRedirects() ?: return call
        return builder(call).run {
            val clear = call.argument(ParameterNames.CLEAR) ?: irFalse()
            irInvoke(symbols[CoreFunction.CONFIRM_VERIFIED_STATIC], irStaticMocks(statics), clear)
        }
    }

    private fun lowerMockConstructor(call: IrCall): IrExpression {
        val targets = call.constructorRedirects() ?: return call
        val block = call.argument(ParameterNames.BLOCK)
        return builder(call).irBlock {
            val flags = irRecordPrivateCalls(call)
            val installed = targets.map { irConstructorRedirect(call, it, flags?.invoke()) ?: return call }
            +irInstall(CoreFunction.MOCK_CONSTRUCTOR, CoreFunction.MOCK_CONSTRUCTOR_WITH_BLOCK, installed, block)
        }.also { it.patchDeclarationParents(currentScope!!.scope.getLocalDeclarationParent()) }
    }

    private fun lowerUnmockConstructor(call: IrCall): IrExpression {
        val targets = call.constructorRedirects() ?: return call
        return builder(call).run {
            val keys = irArrayOf(context.irBuiltIns.anyType, targets.map { irString(it.key) })
            irInvoke(symbols[CoreFunction.UNMOCK], keys)
        }
    }

    private fun lowerClearConstructorMock(call: IrCall): IrExpression {
        val targets = call.constructorRedirects() ?: return call
        return builder(call).run {
            val stringType = context.irBuiltIns.stringType
            irInvoke(
                symbols[CoreFunction.CLEAR_CONSTRUCTOR],
                irArrayOf(stringType, targets.map { irString(it.key) }),
                irArrayOf(stringType, targets.map { irString(it.target.name.asString()) }),
                call.argument(ParameterNames.ANSWERS) ?: irTrue(),
                call.argument(ParameterNames.RECORDED_CALLS) ?: irTrue(),
                call.argument(ParameterNames.CHILD_MOCKS) ?: irTrue(),
            )
        }
    }

    private fun IrCall.constructorRedirects(): List<ConstructorRedirect>? {
        val name = symbol.owner.name
        val classes = constructorClasses
            ?: return null.also { unsupported(this, "An argument of '$name' must be a class literal") }
        return classes.map { target ->
            file.redirects.constructors(target) ?: return null.also {
                file.diagnostics.objectNotInterceptable(
                    this,
                    target.name.asString(),
                    "it has no constructor interception point: add it to lokki { mockable(...) } of its module",
                )
            }
        }
    }

    private fun IrBuilderWithScope.irConstructorRedirect(
        call: IrCall,
        redirect: ConstructorRedirect,
        flags: IrExpression?,
    ): IrExpression? {
        val site = sites.constructed[redirect.target] ?: return null.also {
            unsupported(call, "'${redirect.target.name}' cannot be mocked by its constructors")
        }
        val implementation = constructedClasses.getValue(redirect.target)
        val builtIns = context.irBuiltIns
        val install = context.buildLambda(startOffset, endOffset, builtIns.unitType)
        val value = install.addValueParameter("mocks", builtIns.anyNType)
        val redirectType = redirect.redirect.defaultType.makeNullable()
        install.body = DeclarationIrBuilder(context, install.symbol, startOffset, endOffset).irBlockBody {
            val created = irInvoke(
                implementation.primaryConstructor!!,
                irAs(irGet(value), symbols[CoreClass.CONSTRUCTOR_MOCKS].defaultType),
            )
            val installed = irIfThenElse(redirectType, irEqualsNull(irGet(value)), irNull(), created)
            +irInvoke(redirect.property.setter!!, installed)
        }
        val arguments = listOfNotNull(
            irString(redirect.key),
            irString(redirect.target.name.asString()),
            irFactory(symbols, mocks.getValue(site.representative.key), site.representative.types),
            install.toLambda(builtIns),
            flags,
        )
        val constructor = if (flags == null) CoreConstructor.REDIRECT else CoreConstructor.REDIRECT_WITH_FLAGS
        return irInvoke(symbols[constructor], *arguments.toTypedArray())
    }

    private fun IrCall.staticRedirects(): List<StaticRedirect>? {
        val name = symbol.owner.name
        staticFileArguments?.let { files ->
            return files.flatMap { element ->
                val className = element.fileClassName ?: return null.also {
                    unsupported(this, "An argument of '$name' must be a string literal with the name of a file class")
                }
                file.redirects.file(className).ifEmpty {
                    file.diagnostics.objectNotInterceptable(
                        element,
                        className,
                        "it is not a file class with interception points: add its functions or package to " +
                            "lokki { mockable(...) } of the module that declares them",
                    )
                    return null
                }
            }
        }
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

    private fun IrBuilderWithScope.irStaticMocks(statics: List<StaticRedirect>): IrExpression =
        irArrayOf(
            context.irBuiltIns.anyType,
            statics.map { irInvoke(symbols[CoreFunction.STATIC_REDIRECT_OF], irString(it.key), irString(it.name)) },
        )

    private fun IrBuilderWithScope.irRedirect(
        target: IrExpression,
        redirect: Redirect,
        flags: IrExpression? = null,
    ): IrExpression = irRedirect(symbols, mocks.getValue(redirect.site.key), target, redirect, flags)

    private fun IrBuilderWithScope.irFlags(call: IrCall, base: Int, named: List<Pair<String, Int>>): IrExpression {
        var constant = base
        val conditional = ArrayList<IrExpression>()
        for ((name, flag) in named) {
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
