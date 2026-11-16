package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.expressions.IrCallableReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichCallableReference

internal val IrExpression.referencedDeclaration: IrDeclarationWithName?
    get() = when (this) {
        is IrFunctionReference -> (reflectionTarget ?: symbol).owner
        is IrCallableReference<*> -> symbol.owner
        is IrRichCallableReference<*> -> reflectionTargetSymbol?.owner
        else -> null
    } as? IrDeclarationWithName

internal val IrExpression.isUnboundReference: Boolean
    get() = when (this) {
        is IrCallableReference<*> -> arguments.all { it == null }
        is IrRichCallableReference<*> -> boundValues.isEmpty()
        else -> false
    }
