package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.addFriendAwareFakeOverrides
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.collect.InjectionSource
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockParameterField
import io.lokki.compiler.ir.collect.MockParameterGroup
import io.lokki.compiler.ir.irCast
import io.lokki.compiler.ir.irInvoke
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name

internal class MockParameterFactories(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val injections = MockInjections(lokki)

    fun generate(file: FileContext, mocks: Map<MockClassKey, IrClass>) {
        for (group in file.fields.parameterGroups) {
            if (group.parameters.all { it.problem == null }) generate(group, file.ir, mocks)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun generate(group: MockParameterGroup, file: IrFile, mocks: Map<MockClassKey, IrClass>) {
        val function = group.function
        val owner = function.parentAsClass
        val signature = function.parameters.filter { it.kind == IrParameterKind.Regular }
            .joinToString(",") { it.type.render() }
        val hash = "${owner.kotlinFqName}#${function.name}($signature)".hashCode().toHexString()
        val filePart = PackagePartClassUtils.getFilePartShortName(file.name)
        val factory = context.irFactory.buildClass {
            startOffset = function.startOffset
            endOffset = function.endOffset
            name = Name.identifier("${owner.name}\$LokkiParameters\$$hash\$$filePart")
            origin = lokki.origin
        }
        factory.superTypes = listOf(symbols[CoreClass.MOCK_PARAMETER_FACTORY].defaultType)
        factory.createThisReceiverParameter()
        file.addChild(factory)
        factory.addConstructor {
            startOffset = function.startOffset
            endOffset = function.endOffset
            origin = lokki.origin
            isPrimary = true
        }.apply {
            body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
                +IrInstanceInitializerCallImpl(startOffset, endOffset, factory.symbol, context.irBuiltIns.unitType)
            }
        }
        factory.addFriendAwareFakeOverrides(lokki.typeSystem)
        factory.implementCreate(group, mocks)
        factory.implementInject(group)
        group.parameters.forEach { it.annotateWith(factory) }
    }

    private fun IrClass.implementCreate(group: MockParameterGroup, mocks: Map<MockClassKey, IrClass>) {
        val create = implementing(CoreFunction.PARAMETER_CREATE)
        val (index, values) = create.nonDispatchParameters
        create.body = DeclarationIrBuilder(context, create.symbol).irBlockBody {
            val branches = group.parameters.map { field ->
                irBranch(irEquals(irGet(index), irInt(field.index)), irCreate(field, group, values, mocks))
            }
            +irReturn(irWhen(context.irBuiltIns.anyNType, branches + irElseBranch(irNull())))
        }
    }

    private fun IrClass.implementInject(group: MockParameterGroup) {
        val inject = implementing(CoreFunction.PARAMETER_INJECT)
        val (index, instance, values) = inject.nonDispatchParameters
        inject.body = DeclarationIrBuilder(context, inject.symbol).irBlockBody {
            for (field in group.parameters) {
                val injection = field.injection?.takeIf { it.properties.isNotEmpty() } ?: continue
                val type = field.parameter.type.makeNotNull()
                val read: IrBuilderWithScope.(InjectionSource) -> IrExpression = { irValue(it, group, values) }
                val injected = with(injections) {
                    if (field.kind == IntrinsicKind.SPY) {
                        irWithoutStateChanges(inject) { irInjectProperties(irGet(instance), type, injection, read) }
                    } else {
                        irBlock { irInjectProperties(irGet(instance), type, injection, read) }
                    }
                }
                +irIfThen(context.irBuiltIns.unitType, irEquals(irGet(index), irInt(field.index)), injected)
            }
        }
    }

    private fun IrBuilderWithScope.irCreate(
        field: MockParameterField,
        group: MockParameterGroup,
        values: IrValueParameter,
        mocks: Map<MockClassKey, IrClass>,
    ): IrExpression {
        val read: IrBuilderWithScope.(InjectionSource, IrType, String) -> IrExpression = { source, type, _ ->
            irCast(irValue(source, group, values), type)
        }
        val type = field.parameter.type.makeNotNull() as IrSimpleType
        val site = field.site
        val injection = field.injection
        if (injection != null && (site == null || site.types.form is MockForm.Construction)) {
            return with(injections) { irCreate(type, field.name, site, injection, mocks, read) }
        }
        site!!
        val mock = mocks.getValue(site.key)
        val spec = irSpec(symbols, irString(field.name), irInt(field.flags(site.types.form)))
        return when (site.types.form) {
            MockForm.Mock, MockForm.Delegation -> irNewMock(symbols, mock, site.types, spec)
            is MockForm.Construction -> irConstruct(mock, site.types.typeArguments, listOf(spec))
        }
    }

    private fun IrBuilderWithScope.irValue(
        source: InjectionSource,
        group: MockParameterGroup,
        values: IrValueParameter,
    ): IrExpression {
        val index = group.parameters.first { it.parameter == source.key }.index
        return irInvoke(symbols[CoreFunction.PARAMETER_VALUE], irInt(index), receiver = irGet(values))
    }

    private fun MockParameterField.flags(form: MockForm): Int =
        when (kind) {
            IntrinsicKind.SPY -> form.spyFlags
            IntrinsicKind.RELAXED_MOCK -> BuildConfig.FLAG_RELAXED
            else -> MOCK_FLAGS.fold(0) { flags, (name, flag) ->
                if ((annotation.argument(name) as? IrConst)?.value == true) flags or flag else flags
            }
        }

    private fun MockParameterField.annotateWith(factory: IrClass) {
        val type = factory.defaultType
        parameter.annotations += DeclarationIrBuilder(context, parameter.symbol).run {
            irAnnotation(symbols[CoreConstructor.MOCK_PARAMETER].symbol).apply {
                arguments[0] = IrClassReferenceImpl(
                    startOffset,
                    endOffset,
                    context.irBuiltIns.kClassClass.typeWith(type),
                    factory.symbol,
                    type,
                )
                arguments[1] = irInt(index)
            }
        }
    }

    private fun IrClass.implementing(entry: CoreFunction): IrSimpleFunction =
        functions.single { symbols[entry].symbol in it.overriddenSymbols }.apply {
            isFakeOverride = false
            origin = lokki.origin
            modality = Modality.FINAL
            val owner = this@implementing
            parameters = parameters.map {
                if (it.kind == IrParameterKind.DispatchReceiver) owner.dispatchReceiverFor(this) else it
            }
        }
}
