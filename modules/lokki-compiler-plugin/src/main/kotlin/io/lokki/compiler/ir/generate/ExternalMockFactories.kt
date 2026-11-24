package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.addFriendAwareFakeOverrides
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.collect.ExternalMockField
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.valueType
import io.lokki.compiler.ir.irCast
import io.lokki.compiler.ir.irThrowLokkiException
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
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
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
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name

internal class ExternalMockFactories(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    fun generate(file: FileContext, mocks: Map<MockClassKey, IrClass>) {
        for ((owner, fields) in file.external.owners) {
            if (fields.all { it.site is MockSite.Mocked }) generate(owner, fields, file.ir, mocks)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun generate(owner: IrClass, fields: List<ExternalMockField>, file: IrFile, mocks: Map<MockClassKey, IrClass>) {
        val hash = owner.kotlinFqName.asString().hashCode().toHexString()
        val filePart = PackagePartClassUtils.getFilePartShortName(file.name)
        val factory = context.irFactory.buildClass {
            startOffset = owner.startOffset
            endOffset = owner.endOffset
            name = Name.identifier("${owner.name}\$LokkiExternalMocks\$$hash\$$filePart")
            origin = lokki.origin
        }
        factory.superTypes = listOf(symbols[CoreClass.EXTERNAL_MOCK_FACTORY].defaultType)
        factory.createThisReceiverParameter()
        file.addChild(factory)
        factory.addConstructor {
            startOffset = owner.startOffset
            endOffset = owner.endOffset
            origin = lokki.origin
            isPrimary = true
        }.apply {
            body = DeclarationIrBuilder(context, symbol, startOffset, endOffset).irBlockBody {
                +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
                +IrInstanceInitializerCallImpl(startOffset, endOffset, factory.symbol, context.irBuiltIns.unitType)
            }
        }
        factory.addFriendAwareFakeOverrides(lokki.typeSystem)
        factory.implementCreate(fields, mocks)
        fields.forEach { it.annotateWith(factory) }
    }

    private fun IrClass.implementCreate(fields: List<ExternalMockField>, mocks: Map<MockClassKey, IrClass>) {
        val create = functions.single { symbols[CoreFunction.EXTERNAL_CREATE].symbol in it.overriddenSymbols }
        create.isFakeOverride = false
        create.origin = lokki.origin
        create.modality = Modality.FINAL
        create.parameters = create.parameters.map {
            if (it.kind == IrParameterKind.DispatchReceiver) dispatchReceiverFor(create) else it
        }
        val (index, name, delegate) = create.nonDispatchParameters
        create.body = DeclarationIrBuilder(context, create.symbol).irBlockBody {
            val branches = fields.map { field ->
                irBranch(irEquals(irGet(index), irInt(field.index)), irCreate(field, name, delegate, mocks))
            }
            val otherwise = irElseBranch(irThrowLokkiException(symbols, "no external mock with this index"))
            +irReturn(irWhen(context.irBuiltIns.anyType, branches + otherwise))
        }
    }

    private fun IrBuilderWithScope.irCreate(
        field: ExternalMockField,
        name: IrValueParameter,
        delegate: IrValueParameter,
        mocks: Map<MockClassKey, IrClass>,
    ): IrExpression {
        val site = field.site as MockSite.Mocked
        val mock = mocks.getValue(site.key)
        val property = "${field.property.parentAsClass.kotlinFqName}.${field.property.name}"
        if (field.kind == IntrinsicKind.EXTERNAL_SPY) {
            val spec = irSpec(symbols, irGet(name), irInt(site.types.form.spyFlags))
            val bean = irCast(irGet(delegate), field.property.valueType.makeNotNull())
            return irBlock {
                +irIfThen(
                    context.irBuiltIns.unitType,
                    irEqualsNull(irGet(delegate)),
                    irThrowLokkiException(symbols, "$property is a spy of an existing object, but no object was passed"),
                )
                +irDelegatingSpy(symbols, mock, site.types, bean, spec)
            }
        }
        val flags = MOCK_FLAGS.fold(0) { flags, (parameter, flag) ->
            if ((field.annotation.argument(parameter) as? IrConst)?.value == true) flags or flag else flags
        }
        return irIfThenElse(
            context.irBuiltIns.anyType,
            irEqualsNull(irGet(delegate)),
            irNewMock(symbols, mock, site.types, irSpec(symbols, irGet(name), irInt(flags))),
            irThrowLokkiException(symbols, "$property is a mock, not a spy: it takes no object to wrap"),
        )
    }

    private fun ExternalMockField.annotateWith(factory: IrClass) {
        val field = property.backingField!!
        val type = factory.defaultType
        field.annotations += DeclarationIrBuilder(context, field.symbol).run {
            irAnnotation(symbols[CoreConstructor.EXTERNAL_MOCK_FIELD].symbol).apply {
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
}
