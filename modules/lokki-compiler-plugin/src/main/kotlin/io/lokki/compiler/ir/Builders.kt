package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith

internal fun IrBuilder.irArrayOf(elementType: IrType, elements: List<IrVarargElement>): IrExpression {
    val builtIns = context.irBuiltIns
    val arrayType = builtIns.arrayClass.typeWith(elementType)
    return irCall(builtIns.arrayOf, arrayType, listOf(elementType)).apply {
        arguments[0] = IrVarargImpl(startOffset, endOffset, arrayType, elementType, elements)
    }
}
