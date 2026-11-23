package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.addFriendAwareFakeOverrides
import io.lokki.compiler.ir.collect.ConstructedSite
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.irThrowLokkiException
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.Name

internal class ConstructedClasses(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    fun generate(
        file: FileContext,
        sites: Map<IrClass, ConstructedSite>,
        mocks: Map<MockClassKey, IrClass>,
    ): Map<IrClass, IrClass> = sites.mapValues { (_, site) -> generate(site, file.ir, mocks) }

    @OptIn(ExperimentalStdlibApi::class)
    private fun generate(site: ConstructedSite, file: IrFile, mocks: Map<MockClassKey, IrClass>): IrClass {
        val target = site.redirect.target
        val filePart = PackagePartClassUtils.getFilePartShortName(file.name)
        val hash = site.redirect.key.hashCode().toHexString()
        val implementation = context.irFactory.buildClass {
            startOffset = target.startOffset
            endOffset = target.endOffset
            name = Name.identifier("${target.name}\$LokkiConstructed\$$hash\$$filePart")
            origin = lokki.origin
            visibility = DescriptorVisibilities.PRIVATE
        }
        implementation.superTypes = listOf(site.redirect.redirect.defaultType)
        implementation.createThisReceiverParameter()
        file.addChild(implementation)
        val field = implementation.addMocksField()
        implementation.addFriendAwareFakeOverrides(lokki.typeSystem)
        for (constructor in site.redirect.constructors) {
            val abstract = site.redirect.create(constructor)!!
            val create = implementation.functions.single { abstract.symbol in it.overriddenSymbols }
            create.materialize(implementation)
            val instance = site.instances[constructor]
            create.body = DeclarationIrBuilder(context, create.symbol).irBlockBody {
                if (instance == null) {
                    +irThrowLokkiException(
                        symbols,
                        "a constructor of ${target.name} is not visible from the file that calls mockConstructor",
                    )
                    return@irBlockBody
                }
                val values = create.nonDispatchParameters
                val spec = irInvoke(
                    symbols[CoreFunction.CONSTRUCTED_SPEC],
                    irArrayOf(context.irBuiltIns.anyNType, values.map(::irGet)),
                    receiver = irGetField(irGet(create.dispatchReceiverParameter!!), field),
                )
                val mock = mocks.getValue(instance.key)
                val created = irConstruct(mock, instance.types.typeArguments, values.map { irGet(it) } + spec)
                +irReturn(irImplicitCast(created, create.returnType))
            }
        }
        return implementation
    }

    private fun IrClass.addMocksField(): IrField {
        val type = symbols[CoreClass.CONSTRUCTOR_MOCKS].defaultType
        val field = addField {
            name = MOCKS
            this.type = type
            origin = lokki.origin
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
        }
        val constructor = addConstructor {
            startOffset = this@addMocksField.startOffset
            endOffset = this@addMocksField.endOffset
            origin = lokki.origin
            isPrimary = true
        }
        val value = constructor.addValueParameter("mocks", type)
        field.initializer = DeclarationIrBuilder(context, field.symbol).run { irExprBody(irGet(value)) }
        val owner = this
        constructor.body = DeclarationIrBuilder(context, constructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
            +IrInstanceInitializerCallImpl(startOffset, endOffset, owner.symbol, context.irBuiltIns.unitType)
        }
        return field
    }

    private fun IrSimpleFunction.materialize(owner: IrClass) {
        isFakeOverride = false
        origin = lokki.origin
        modality = Modality.FINAL
        parameters = parameters.map {
            if (it.kind == IrParameterKind.DispatchReceiver) owner.dispatchReceiverFor(this) else it
        }
    }

    private companion object {
        val MOCKS = Name.identifier("mocks")
    }
}
