package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

internal fun IrFunctionAccessExpression.argument(name: String): IrExpression? =
    symbol.owner.parameters.find { it.name.asString() == name }?.let { arguments[it.indexInParameters] }

internal fun IrExpression.constructorCall(): IrConstructorCall? =
    when {
        this is IrConstructorCall -> this
        this is IrBlock && origin == IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL ->
            (statements.lastOrNull() as? IrExpression)?.constructorCall()
        else -> null
    }

internal val IrExpression.temporary: IrVariable?
    get() = ((this as? IrGetValue)?.symbol?.owner as? IrVariable)
        ?.takeIf { it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE && !it.isVar }

internal fun IrExpression.unwrapTemporary(): IrExpression = temporary?.initializer ?: this

internal val IrExpression.wrapped: IrExpression?
    get() = when {
        this is IrTypeOperatorCall && operator in CASTS -> argument
        this is IrCall && origin == IrStatementOrigin.EXCLEXCL -> arguments[0]
        else -> null
    }

internal fun IrExpression.rewrap(value: IrExpression) {
    when (this) {
        is IrTypeOperatorCall -> argument = value
        is IrCall -> arguments[0] = value
        else -> error("not a cast or '!!'")
    }
}

internal inline fun IrElement.contains(crossinline predicate: (IrElement) -> Boolean): Boolean =
    find(predicate, descend = { true }) != null

internal inline fun IrElement.find(
    crossinline predicate: (IrElement) -> Boolean,
    crossinline descend: (IrElement) -> Boolean,
): IrElement? {
    var found: IrElement? = null
    acceptVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
            when {
                found != null -> {}
                predicate(element) -> found = element
                descend(element) -> element.acceptChildrenVoid(this)
            }
        }
    })
    return found
}

internal val CASTS = setOf(
    IrTypeOperator.CAST,
    IrTypeOperator.IMPLICIT_CAST,
    IrTypeOperator.IMPLICIT_NOTNULL,
    IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
)
