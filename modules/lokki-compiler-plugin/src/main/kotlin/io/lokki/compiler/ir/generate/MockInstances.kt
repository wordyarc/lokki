package io.lokki.compiler.ir.generate

import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.collect.MockedTypes
import io.lokki.compiler.ir.collect.mockedClass
import io.lokki.compiler.ir.collect.referencedTypeParameters
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.SpecialNames

internal class MockInstances(private val context: IrPluginContext, private val symbols: LokkiSymbols) {
    fun IrBuilderWithScope.irSpec(name: String, flags: IrExpression): IrExpression =
        irCallConstructor(symbols.mockSpec.primaryConstructor!!.symbol, emptyList()).apply {
            arguments[0] = irString(name)
            arguments[1] = flags
        }

    fun IrBuilderWithScope.irConstruct(
        mock: IrClass,
        typeArguments: List<IrType>,
        arguments: List<IrExpression>,
    ): IrExpression =
        irCallConstructor(mock.primaryConstructor!!.symbol, typeArguments).apply {
            type = mock.typeWith(typeArguments)
            arguments.forEachIndexed { index, argument -> this.arguments[index] = argument }
        }

    fun IrBuilderWithScope.irAllocate(type: IrSimpleType, init: List<IrExpression>): IrExpression = irBlock {
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
            init.forEachIndexed { index, argument -> arguments[index + 1] = argument }
        }
        +irGet(instance)
    }

    fun IrBuilderWithScope.irNewMock(mock: IrClass, types: MockedTypes, spec: IrExpression): IrExpression {
        val typeArguments = types.type.referencedTypeParameters.map { it.defaultType }
        return when {
            types.type.mockedClass.isInterface -> irConstruct(mock, typeArguments, listOf(spec))
            else -> irAllocate(mock.typeWith(typeArguments), listOf(spec))
        }
    }

    fun IrBuilderWithScope.irDelegatingSpy(
        mock: IrClass,
        types: MockedTypes,
        instance: IrExpression,
        spec: IrExpression,
    ): IrExpression {
        val typeArguments = types.type.referencedTypeParameters.map { it.defaultType }
        val arguments = listOf(instance, spec)
        return when {
            types.type.mockedClass.isInterface -> irConstruct(mock, typeArguments, arguments)
            else -> irAllocate(mock.typeWith(typeArguments), arguments)
        }
    }

    fun IrBuilderWithScope.irFactory(mock: IrClass, types: MockedTypes): IrExpression {
        val specType = symbols.mockSpec.defaultType
        val anyType = context.irBuiltIns.anyType
        val factory = context.irFactory.buildFun {
            startOffset = this@irFactory.startOffset
            endOffset = this@irFactory.endOffset
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = anyType
        }
        val spec = factory.addValueParameter("spec", specType)
        factory.body = DeclarationIrBuilder(context, factory.symbol, startOffset, endOffset).irBlockBody {
            +irReturn(irNewMock(mock, types, irGet(spec)))
        }
        val type = context.irBuiltIns.functionN(1).typeWith(specType, anyType)
        return IrFunctionExpressionImpl(startOffset, endOffset, type, factory, IrStatementOrigin.LAMBDA)
    }
}

internal fun IrExpression.replaceConstructorCall(
    type: IrType,
    replacement: (IrConstructorCall) -> IrExpression,
): IrExpression =
    when (this) {
        is IrConstructorCall -> replacement(this)
        is IrBlock -> apply {
            statements[statements.lastIndex] =
                (statements.last() as IrExpression).replaceConstructorCall(type, replacement)
            this.type = type
        }
        else -> error("not a constructor call: ${render()}")
    }

internal const val RELAXED_FLAG = 1
internal const val RELAX_UNIT_FUN_FLAG = 2
internal const val SPY_FLAG = 4
internal const val DELEGATE_FLAG = 8
