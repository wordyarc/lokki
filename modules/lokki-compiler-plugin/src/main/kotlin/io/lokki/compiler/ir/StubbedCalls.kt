package io.lokki.compiler.ir

import io.lokki.compiler.BLOCK_KINDS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.MatcherNames
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.VERIFY_BLOCKS
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

internal sealed interface Expected

internal class StubbedCall(
    val call: IrCall,
    val receiver: IrExpression?,
    val arguments: List<IrExpression?>,
    val order: List<Int>,
) : Expected {
    val allAny: Boolean
        get() = arguments.firstOrNull().isAllAny

    val callsSuspend: Boolean
        get() = receiver?.callsSuspend() == true || arguments.any { it?.callsSuspend() == true }
}

internal class NoCalls(val call: IrCall) : Expected {
    private val receiver = call.symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }

    val mocks: IrExpression
        get() = call.arguments[receiver]!!

    val mocksClass: IrClass
        get() = receiver.type.erasedUpperBound
}

internal class NoConstructedCalls(val call: IrCall) : Expected

internal fun IrSimpleFunction.stubbedCall(): StubbedCall? =
    (body as? IrBlockBody)?.statements?.singleOrNull()?.stubbedCall()

internal fun IrSimpleFunction.verifiedStatements(): List<Expected>? {
    val statements = blockStatements()
    if (statements.isEmpty()) return null
    return statements.map { it.noCalls() ?: it.noConstructedCalls() ?: it.stubbedCall() ?: return null }
}

internal fun IrSimpleFunction.excludedCalls(): List<StubbedCall>? {
    val statements = blockStatements()
    if (statements.isEmpty()) return null
    return statements.map { it.stubbedCall() ?: return null }
}

private fun IrSimpleFunction.blockStatements(): List<IrStatement> =
    (body as? IrBlockBody)?.statements.orEmpty().filterNot { it.isUnit }

private val IrStatement.isUnit: Boolean
    get() {
        val value = if (this is IrReturn) value else this
        return value is IrGetObjectValue && value.type.isUnit()
    }

private fun IrStatement.stubbedCall(): StubbedCall? = (unwrapped() as? IrExpression)?.memberCall()

private fun IrStatement.noCalls(): NoCalls? =
    (unwrapped() as? IrCall)?.takeIf { it.intrinsicKind == IntrinsicKind.WAS_NOT_CALLED }?.let(::NoCalls)

private fun IrStatement.noConstructedCalls(): NoConstructedCalls? =
    (unwrapped() as? IrCall)?.takeIf { it.intrinsicKind == IntrinsicKind.CONSTRUCTED }?.let(::NoConstructedCalls)

private fun IrStatement.unwrapped(): IrStatement? {
    var expression: IrStatement? = if (this is IrReturn) value else this
    while (true) {
        expression = (expression as? IrExpression)?.wrapped ?: return expression
    }
}

internal fun IrExpression.memberCall(): StubbedCall? {
    if (this is IrBlock && origin == IrStatementOrigin.SAFE_CALL) return safeCall()
    var expression: IrStatement? = this
    var frozen = emptyList<IrVariable>()
    if (expression is IrBlock && expression.origin == IrStatementOrigin.ARGUMENTS_REORDERING_FOR_CALL) {
        frozen = expression.statements.filterIsInstance<IrVariable>()
        expression = expression.statements.last()
    }
    val call = expression as? IrCall ?: return null
    if (call.intrinsicKind == IntrinsicKind.WITH_CONTEXT || call.intrinsicKind == IntrinsicKind.DYNAMIC_CALL) return null
    fun frozenIndex(argument: IrExpression?) = frozen.indexOfFirst { it.symbol == (argument as? IrGetValue)?.symbol }
    fun IrExpression.thawed() = frozen.getOrNull(frozenIndex(this))?.initializer ?: this
    val receiver = call.dispatchReceiver?.thawed()
    if (receiver == null && !call.symbol.owner.isTopLevel) return null
    val arguments = call.symbol.owner.nonDispatchParameters.map { call.arguments[it] }
    return StubbedCall(
        call,
        receiver,
        arguments.map { it?.thawed() },
        arguments.indices.sortedBy { index -> frozenIndex(arguments[index]).takeIf { it >= 0 } ?: frozen.size },
    )
}

private fun IrBlock.safeCall(): StubbedCall? {
    val subject = statements.firstOrNull() as? IrVariable ?: return null
    val selector = (statements.lastOrNull() as? IrWhen)?.branches?.lastOrNull()?.result ?: return null
    val selected = generateSequence(selector) { it.wrapped }.last().memberCall() ?: return null
    fun IrExpression.subjectValue(): IrExpression? {
        val value = generateSequence(this) { it.wrapped }.last()
        return if ((value as? IrGetValue)?.symbol == subject.symbol) subject.initializer else this
    }
    return StubbedCall(
        selected.call,
        selected.receiver?.subjectValue(),
        selected.arguments.map { it?.subjectValue() },
        selected.order,
    )
}

internal fun IrSimpleFunction.isLink(lokki: LokkiIrContext): Boolean =
    dispatchReceiverParameter != null && isOverridable(lokki) && !isMethodOfAny()

private val IrExpression?.isAllAny: Boolean
    get() = when (this) {
        is IrVararg -> (elements.firstOrNull() as? IrExpression).isAllAny
        is IrCall -> intrinsicKind == IntrinsicKind.MATCHER && symbol.owner.name.asString() == MatcherNames.ALL_ANY
        else -> false
    }

internal fun IrExpression.callsSuspend(): Boolean {
    val suspending = find(
        predicate = { it is IrCall && it.symbol.owner.isSuspend },
        descend = { it !is IrFunctionExpression || !it.function.isSuspend },
    )
    return suspending != null
}

internal fun IrCall.blockCalls(): List<StubbedCall> {
    val block = (arguments.lastOrNull() as? IrFunctionExpression)?.function ?: return emptyList()
    return when (intrinsicKind) {
        in STUB_BLOCKS -> listOfNotNull(block.stubbedCall())
        in VERIFY_BLOCKS -> block.verifiedStatements().orEmpty().filterIsInstance<StubbedCall>()
        IntrinsicKind.EXCLUDE_BLOCK -> block.excludedCalls().orEmpty()
        else -> emptyList()
    }
}

internal fun IrCall.blockStatementCalls(): List<IrCall> {
    val kind = intrinsicKind
    if (kind !in BLOCK_KINDS) return emptyList()
    val block = (arguments.lastOrNull() as? IrFunctionExpression)?.function ?: return emptyList()
    return block.blockStatements().mapNotNull { statement ->
        (statement.unwrapped() as? IrCall)?.takeIf { call ->
            when (call.intrinsicKind) {
                IntrinsicKind.WITH_CONTEXT -> true
                IntrinsicKind.WAS_NOT_CALLED -> kind in VERIFY_BLOCKS
                else -> false
            }
        }
    }
}

internal fun IrCall.replaceBlockCalls(replacement: (IrCall, IrSimpleFunction) -> IrExpression?) {
    if (intrinsicKind !in BLOCK_KINDS) return
    val block = (arguments.lastOrNull() as? IrFunctionExpression)?.function ?: return
    fun IrExpression.replaced(): IrExpression {
        val wrapped = wrapped
        when {
            this is IrReturn -> value = value.replaced()
            wrapped != null -> rewrap(wrapped.replaced())
            this is IrCall -> return replacement(this, block) ?: this
        }
        return this
    }
    (block.body as? IrBlockBody)?.statements?.replaceAll { if (it is IrExpression) it.replaced() else it }
}

internal fun IrExpression.links(lokki: LokkiIrContext): List<StubbedCall> {
    wrapped?.let { return it.links(lokki) }
    val link = memberCall()?.takeIf { it.call.symbol.owner.isLink(lokki) } ?: return emptyList()
    return link.receiver?.links(lokki).orEmpty() + link
}
