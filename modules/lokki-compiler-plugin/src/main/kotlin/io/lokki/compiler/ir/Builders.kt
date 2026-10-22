package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass

internal fun IrBuilder.irArrayOf(elementType: IrType, elements: List<IrVarargElement>): IrExpression {
    val builtIns = context.irBuiltIns
    return irCall(builtIns.arrayOf, builtIns.arrayClass.typeWith(elementType), listOf(elementType)).apply {
        arguments[0] = irVararg(elementType, elements)
    }
}

internal fun IrBuilder.irVararg(elementType: IrType, elements: List<IrVarargElement>): IrExpression =
    IrVarargImpl(startOffset, endOffset, context.irBuiltIns.arrayClass.typeWith(elementType), elementType, elements)

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
