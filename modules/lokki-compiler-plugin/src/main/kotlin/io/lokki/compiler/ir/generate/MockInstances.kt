package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreClass
import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.buildLambda
import io.lokki.compiler.ir.collect.MockForm
import io.lokki.compiler.ir.collect.MockedTypes
import io.lokki.compiler.ir.collect.mockedClass
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.irJavaClass
import io.lokki.compiler.ir.toLambda
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render

internal val MockForm.spyFlags: Int
    get() = if (this == MockForm.Delegation) BuildConfig.FLAG_SPY or BuildConfig.FLAG_DELEGATE else BuildConfig.FLAG_SPY

internal fun IrBuilderWithScope.irSpec(symbols: LokkiSymbols, name: IrExpression, flags: IrExpression): IrExpression =
    irInvoke(symbols[CoreConstructor.MOCK_SPEC], name, flags)

internal fun IrBuilderWithScope.irConstruct(
    mock: IrClass,
    typeArguments: List<IrType>,
    arguments: List<IrExpression>,
): IrExpression =
    irCallConstructor(mock.primaryConstructor!!.symbol, typeArguments).apply {
        type = mock.typeWith(typeArguments)
        arguments.forEachIndexed { index, argument -> this.arguments[index] = argument }
    }

internal fun IrBuilderWithScope.irNewMock(
    symbols: LokkiSymbols,
    mock: IrClass,
    types: MockedTypes,
    spec: IrExpression,
): IrExpression = irInstance(symbols, mock, types, listOf(spec))

internal fun IrBuilderWithScope.irDelegatingSpy(
    symbols: LokkiSymbols,
    mock: IrClass,
    types: MockedTypes,
    instance: IrExpression,
    spec: IrExpression,
): IrExpression = irInstance(symbols, mock, types, listOf(instance, spec))

internal fun IrBuilderWithScope.irFactory(symbols: LokkiSymbols, mock: IrClass, types: MockedTypes): IrExpression {
    val factory = context.buildLambda(startOffset, endOffset, context.irBuiltIns.anyType)
    val spec = factory.addValueParameter("spec", symbols[CoreClass.MOCK_SPEC].defaultType)
    factory.body = DeclarationIrBuilder(context, factory.symbol, startOffset, endOffset).irBlockBody {
        +irReturn(irNewMock(symbols, mock, types, irGet(spec)))
    }
    return factory.toLambda(context.irBuiltIns)
}

private fun IrBuilderWithScope.irInstance(
    symbols: LokkiSymbols,
    mock: IrClass,
    types: MockedTypes,
    arguments: List<IrExpression>,
): IrExpression {
    val typeArguments = types.typeArguments
    return when {
        types.type.mockedClass.isInterface -> irConstruct(mock, typeArguments, arguments)
        else -> irAllocate(symbols, mock.typeWith(typeArguments), arguments)
    }
}

private fun IrBuilderWithScope.irAllocate(
    symbols: LokkiSymbols,
    type: IrSimpleType,
    init: List<IrExpression>,
): IrExpression = irBlock {
    val mock = type.classOrFail.owner
    val instance = irTemporary(
        irInvoke(symbols[CoreFunction.ALLOCATE], irJavaClass(symbols, type), type = type, typeArguments = listOf(type))
    )
    +irInvoke(mock.functions.single { it.name == INIT }, *init.toTypedArray(), receiver = irGet(instance))
    +irGet(instance)
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
