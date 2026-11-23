package io.lokki.compiler.ir.lower

import io.lokki.compiler.FINALIZER_REASON
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.isClassFinalizer
import io.lokki.compiler.ir.isOverridable
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.replaceBlockCalls
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.isNullableNothing
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

internal class DynamicCalls(lokki: LokkiIrContext, private val file: FileContext) : IrElementTransformerVoid() {
    private val context = lokki.pluginContext
    private val anyClass = context.irBuiltIns.anyClass.owner

    override fun visitCall(expression: IrCall): IrExpression {
        expression.replaceBlockCalls { call, block ->
            if (call.intrinsicKind == IntrinsicKind.DYNAMIC_CALL) memberCall(call, block) else null
        }
        return super.visitCall(expression)
    }

    private fun memberCall(call: IrCall, block: IrSimpleFunction): IrExpression? {
        val access = (call.dispatchReceiver as? IrCall)?.takeIf { it.intrinsicKind == IntrinsicKind.DYNAMIC_MEMBER }
            ?: return null
        val accessor = access.symbol.owner
        val receiver = access.arguments[accessor.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }]
            ?: return null
        val literal = access.arguments[accessor.parameters.first { it.kind == IrParameterKind.Regular }]
        val name = (literal as? IrConst)?.value as? String
            ?: return unresolved(call, "the name of the member must be a string literal")
        val parameter = call.symbol.owner.parameters.first { it.kind == IrParameterKind.Regular }
        val elements = (call.arguments[parameter] as? IrVararg)?.elements.orEmpty()
        val values = elements.map {
            it as? IrExpression ?: return unresolved(call, "pass the arguments of '$name' one by one, not as a spread array")
        }
        val member = resolve(call, receiver.type.erasedUpperBound, name, values) ?: return null
        member.notInterceptableReason()?.let { reason ->
            file.diagnostics.notInterceptableMember(call, name, reason)
            file.failedDynamicCalls += call
            return null
        }
        return DeclarationIrBuilder(context, block.symbol, call.startOffset, call.endOffset)
            .irCall(member.symbol, call.type)
            .apply {
                dispatchReceiver = receiver
                member.nonDispatchParameters.forEachIndexed { index, target -> arguments[target] = values[index] }
            }
    }

    private fun resolve(call: IrCall, owner: IrClass, name: String, values: List<IrExpression>): IrSimpleFunction? {
        val named = owner.simpleFunctions()
            .filter { it.name.asString() == name && it.dispatchReceiverParameter != null }
            .toList()
        if (named.isEmpty()) {
            return unresolved(call, "'${owner.name}' has no function '$name', or it is private to a compiled class")
        }
        val fitting = named.filter { member ->
            val parameters = member.nonDispatchParameters
            parameters.size == values.size && parameters.indices.all { values[it].fits(parameters[it]) }
        }
        val exact = fitting.filter { member ->
            member.nonDispatchParameters.withIndex().all { (index, parameter) ->
                values[index].type.erasedUpperBound == parameter.type.erasedUpperBound
            }
        }
        val overloads = named.joinToString { it.signature }
        return fitting.singleOrNull() ?: exact.singleOrNull() ?: unresolved(
            call,
            when {
                fitting.isEmpty() -> "no overload of '$name' takes these arguments. Available: $overloads"
                else -> "the arguments fit several overloads of '$name': $overloads. " +
                    "Give the matchers a type, such as 'any<Int>()'"
            },
        )
    }

    private fun IrExpression.fits(parameter: IrValueParameter): Boolean {
        val argument = type.erasedUpperBound
        return when {
            type.isNullableNothing() -> parameter.type.isNullable()
            argument == anyClass -> true
            else -> argument.isSubclassOf(parameter.type.erasedUpperBound)
        }
    }

    private fun IrSimpleFunction.notInterceptableReason(): String? =
        when {
            DescriptorVisibilities.isPrivate(visibility) -> "it is private, and a mock cannot override it"
            !isVisibleFrom(file.ir) -> "it is not visible from this file, and a mock cannot override it"
            isInline -> "it is inline. Stub the member it calls"
            isClassFinalizer -> FINALIZER_REASON
            !isOverridable -> "it is final"
            else -> null
        }

    private fun unresolved(call: IrCall, reason: String): Nothing? {
        file.diagnostics.unresolvedDynamicCall(call, reason)
        file.failedDynamicCalls += call
        return null
    }
}

private val IrSimpleFunction.signature: String
    get() = nonDispatchParameters.joinToString(prefix = "$name(", postfix = ")") {
        it.type.erasedUpperBound.name.asString()
    }
