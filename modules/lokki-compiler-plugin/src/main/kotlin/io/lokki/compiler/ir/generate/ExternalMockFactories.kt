package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.collect.ExternalMockField
import io.lokki.compiler.ir.collect.MockClassKey
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.valueType
import io.lokki.compiler.ir.irCast
import io.lokki.compiler.ir.irThrowLokkiException
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBranch
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
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

internal class ExternalMockFactories(private val lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols

    fun generate(file: FileContext, mocks: Map<MockClassKey, IrClass>) {
        for ((owner, fields) in file.external.owners) {
            if (fields.all { it.site is MockSite.Mocked }) generate(owner, fields, file.ir, mocks)
        }
    }

    private fun generate(owner: IrClass, fields: List<ExternalMockField>, file: IrFile, mocks: Map<MockClassKey, IrClass>) {
        val name = file.generatedClassName(owner.name, "LokkiExternalMocks", owner.kotlinFqName.asString())
        val factory = file.addImplementation(lokki, name, owner, symbols[CoreClass.EXTERNAL_MOCK_FACTORY])
        factory.implementCreate(fields, mocks)
        fields.forEach { it.annotateWith(factory) }
    }

    private fun IrClass.implementCreate(fields: List<ExternalMockField>, mocks: Map<MockClassKey, IrClass>) {
        val create = implementing(symbols[CoreFunction.EXTERNAL_CREATE], lokki.origin)
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
        return irIfThenElse(
            context.irBuiltIns.anyType,
            irEqualsNull(irGet(delegate)),
            irNewMock(symbols, mock, site.types, irSpec(symbols, irGet(name), irInt(field.annotation.mockFlags))),
            irThrowLokkiException(symbols, "$property is a mock, not a spy: it takes no object to wrap"),
        )
    }

    private fun ExternalMockField.annotateWith(factory: IrClass) {
        val field = property.backingField!!
        field.annotations += DeclarationIrBuilder(context, field.symbol)
            .irFactoryAnnotation(symbols[CoreConstructor.EXTERNAL_MOCK_FIELD], factory, index)
    }
}
