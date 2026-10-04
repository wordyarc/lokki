package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeWith

internal fun IrBuilder.irArrayOf(elementType: IrType, elements: List<IrExpression>): IrExpression {
    val builtIns = context.irBuiltIns
    return irCall(builtIns.arrayOf, builtIns.arrayClass.typeWith(elementType), listOf(elementType)).apply {
        arguments[0] = irVararg(elementType, elements)
    }
}
