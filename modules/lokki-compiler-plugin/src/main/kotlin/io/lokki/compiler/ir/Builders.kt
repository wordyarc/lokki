package io.lokki.compiler.ir

import io.lokki.compiler.CoreConstructor
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.SpecialNames

internal fun IrBuilder.irArrayOf(elementType: IrType, elements: List<IrVarargElement>): IrExpression {
    val builtIns = context.irBuiltIns
    return irCall(builtIns.arrayOf, builtIns.arrayClass.typeWith(elementType), listOf(elementType)).apply {
        arguments[0] = irVararg(elementType, elements)
    }
}

internal fun IrBuilder.irVararg(elementType: IrType, elements: List<IrVarargElement>): IrExpression =
    IrVarargImpl(startOffset, endOffset, context.irBuiltIns.arrayClass.typeWith(elementType), elementType, elements)

internal fun IrBuilder.irGetObject(objectClass: IrClass): IrExpression =
    irGetObjectValue(objectClass.defaultType, objectClass.symbol)

internal fun IrBuilder.irCompanionCall(function: IrSimpleFunction, block: IrSimpleFunction): IrExpression =
    irCall(function.symbol, block.returnType, listOf(block.returnType)).apply {
        arguments[0] = irGetObject(function.parentAsClass)
        arguments[1] = block.toLambda(context.irBuiltIns)
    }

internal fun IrGeneratorContext.buildLambda(
    startOffset: Int,
    endOffset: Int,
    returnType: IrType,
    isSuspend: Boolean = false,
): IrSimpleFunction =
    irFactory.buildFun {
        this.startOffset = startOffset
        this.endOffset = endOffset
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        name = SpecialNames.ANONYMOUS
        visibility = DescriptorVisibilities.LOCAL
        this.returnType = returnType
        this.isSuspend = isSuspend
    }

internal fun IrSimpleFunction.toLambda(builtIns: IrBuiltIns): IrExpression {
    val function = if (isSuspend) builtIns.suspendFunctionN(parameters.size) else builtIns.functionN(parameters.size)
    val type = function.typeWith(parameters.map { it.type } + returnType)
    return IrFunctionExpressionImpl(startOffset, endOffset, type, this, IrStatementOrigin.LAMBDA)
}

internal fun IrBuilderWithScope.irHidden(symbols: LokkiSymbols, message: String): IrAnnotation =
    irAnnotation(symbols.deprecated).apply {
        arguments[0] = irString(message)
        arguments[2] = IrGetEnumValueImpl(
            startOffset,
            endOffset,
            symbols.hiddenDeprecation.parentAsClass.defaultType,
            symbols.hiddenDeprecation.symbol,
        )
    }

internal fun IrBuilder.irArrayGet(symbols: LokkiSymbols, array: IrValueDeclaration, index: Int): IrExpression =
    irCall(symbols.arrayGet.symbol, context.irBuiltIns.anyNType).apply {
        arguments[0] = irGet(array)
        arguments[1] = irInt(index)
    }

internal fun IrBuilder.irJavaClass(symbols: LokkiSymbols, type: IrSimpleType): IrExpression {
    val javaClass = symbols.javaClass
    return irCall(javaClass.symbol, javaClass.returnType.classOrFail.typeWith(type), listOf(type)).apply {
        arguments[0] = IrClassReferenceImpl(
            startOffset,
            endOffset,
            context.irBuiltIns.kClassClass.typeWith(type),
            type.classOrFail,
            type,
        )
    }
}

internal fun IrBuilderWithScope.irThrowLokkiException(symbols: LokkiSymbols, message: String): IrExpression =
    irThrow(
        irCallConstructor(symbols[CoreConstructor.LOKKI_EXCEPTION].symbol, emptyList()).apply {
            arguments[0] = irString(message)
            arguments[1] = irNull()
        }
    )
