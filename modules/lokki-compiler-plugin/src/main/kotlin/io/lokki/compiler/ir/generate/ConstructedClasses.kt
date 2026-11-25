package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.collect.ConstructedSite
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.irThrowLokkiException
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name

internal class ConstructedClasses(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    fun generate(
        file: FileContext,
        sites: Map<IrClass, ConstructedSite>,
        mocks: Map<MockClassKey, IrClass>,
    ): Map<IrClass, IrClass> = sites.mapValues { (_, site) -> generate(site, file.ir, mocks) }

    private fun generate(site: ConstructedSite, file: IrFile, mocks: Map<MockClassKey, IrClass>): IrClass {
        val target = site.redirect.target
        val mocksType = symbols[CoreClass.CONSTRUCTOR_MOCKS].defaultType
        val implementation = file.addImplementation(
            lokki,
            file.generatedClassName(target.name, "LokkiConstructed", site.redirect.key),
            target,
            site.redirect.redirect,
            DescriptorVisibilities.PRIVATE,
        ) { addValueParameter("mocks", mocksType) }
        val field = implementation.addMocksField()
        for (constructor in site.redirect.constructors) {
            val create = implementation.implementing(site.redirect.create(constructor)!!, lokki.origin)
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
        val mocks = primaryConstructor!!.parameters.single()
        val field = addField {
            name = MOCKS
            type = mocks.type
            origin = lokki.origin
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
        }
        field.initializer = DeclarationIrBuilder(context, field.symbol).run { irExprBody(irGet(mocks)) }
        return field
    }

    private companion object {
        val MOCKS = Name.identifier("mocks")
    }
}
