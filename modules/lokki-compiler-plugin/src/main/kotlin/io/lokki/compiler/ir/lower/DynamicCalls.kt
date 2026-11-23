package io.lokki.compiler.ir.lower

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.DynamicNames
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
import org.jetbrains.kotlin.ir.declarations.IrProperty
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
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

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
        val form = call.dynamicForm() ?: return null
        val name = (form.name as? IrConst)?.value as? String
            ?: return unresolved(call, "the name of the member must be a string literal")
        val values = form.values ?: return unresolved(call, form.problem)
        val owner = form.receiver.type.erasedUpperBound
        val member = when (form.kind) {
            DynamicKind.FUNCTION -> resolve(call, owner, name, values)
            DynamicKind.GETTER -> accessor(call, owner, name) { it.getter }
            DynamicKind.SETTER -> accessor(call, owner, name) { it.setter }
        } ?: return null
        member.notInterceptableReason()?.let { reason ->
            file.diagnostics.notInterceptableMember(call, name, reason)
            file.failedDynamicCalls += call
            return null
        }
        return DeclarationIrBuilder(context, block.symbol, call.startOffset, call.endOffset)
            .irCall(member.symbol, if (form.kind == DynamicKind.FUNCTION) call.type else member.returnType)
            .apply {
                dispatchReceiver = form.receiver
                member.nonDispatchParameters.forEachIndexed { index, target -> arguments[target] = values[index] }
            }
    }

    private fun IrCall.dynamicForm(): DynamicForm? {
        val function = symbol.owner
        return when (function.name.asString()) {
            DynamicNames.INVOKE -> viaAccess()?.let { (receiver, name) ->
                val elements = (arguments[function.regularParameter] as? IrVararg)?.elements.orEmpty()
                val values = elements.map { it as? IrExpression ?: return@let spreadForm(receiver, name) }
                DynamicForm(DynamicKind.FUNCTION, receiver, name, values)
            }
            DynamicNames.WITH_ARGUMENTS -> viaAccess()?.let { (receiver, name) ->
                val values = arguments[function.regularParameter]?.listLiteralElements()
                    ?: return@let DynamicForm(DynamicKind.FUNCTION, receiver, name, null, LIST_LITERAL)
                if (values.any { it == null }) return@let spreadForm(receiver, name)
                DynamicForm(DynamicKind.FUNCTION, receiver, name, values.filterNotNull())
            }
            DynamicNames.VALUE -> viaAccess()?.let { (receiver, name) ->
                DynamicForm(DynamicKind.SETTER, receiver, name, listOf(arguments[function.regularParameter]!!))
            }
            DynamicNames.INVOKE_NO_ARGS -> DynamicForm(DynamicKind.FUNCTION, ownReceiver, ownName, emptyList())
            DynamicNames.GET_PROPERTY -> DynamicForm(DynamicKind.GETTER, ownReceiver, ownName, emptyList())
            else -> null
        }
    }

    private fun spreadForm(receiver: IrExpression, name: IrExpression?): DynamicForm {
        val problem = "pass the arguments of '${(name as? IrConst)?.value}' one by one, not as a spread array"
        return DynamicForm(DynamicKind.FUNCTION, receiver, name, null, problem)
    }

    private fun IrCall.viaAccess(): Pair<IrExpression, IrExpression?>? {
        val access = (dispatchReceiver as? IrCall)?.takeIf { it.intrinsicKind == IntrinsicKind.DYNAMIC_MEMBER }
            ?: return null
        val receiver = access.ownReceiverOrNull ?: return null
        return receiver to access.ownName
    }

    private val IrCall.ownReceiverOrNull: IrExpression?
        get() = arguments[symbol.owner.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }]

    private val IrCall.ownReceiver: IrExpression
        get() = ownReceiverOrNull!!

    private val IrCall.ownName: IrExpression?
        get() = arguments[symbol.owner.regularParameter]

    private fun IrExpression.listLiteralElements(): List<IrExpression?>? {
        val list = this as? IrCall ?: return null
        val function = list.symbol.owner
        if (function.kotlinFqName !in LIST_FACTORIES) return null
        val argument = function.parameters.firstOrNull { it.kind == IrParameterKind.Regular }
            ?.let { list.arguments[it] } ?: return emptyList()
        if (argument !is IrVararg) return listOf(argument)
        return argument.elements.map { it as? IrExpression }
    }

    private fun accessor(
        call: IrCall,
        owner: IrClass,
        name: String,
        select: (IrProperty) -> IrSimpleFunction?,
    ): IrSimpleFunction? {
        val property = owner.properties.firstOrNull { it.name.asString() == name && it.getter?.dispatchReceiverParameter != null }
            ?: return unresolved(
                call,
                "'${owner.name}' has no property '$name' with accessors: a private property without custom " +
                    "accessors is read directly from its field",
            )
        return select(property) ?: unresolved(call, "the property '$name' of '${owner.name}' is read-only")
    }

    private fun resolve(call: IrCall, owner: IrClass, name: String, values: List<IrExpression>): IrSimpleFunction? {
        val declared = owner.simpleFunctions()
            .filter { it.name.asString() == name && it.dispatchReceiverParameter != null }
            .toList()
        val named = declared.ifEmpty { privateHooks(owner, name) }
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

    private fun privateHooks(owner: IrClass, name: String): List<IrSimpleFunction> {
        val prefix = "$name${BuildConfig.PRIVATE_HOOK_INFIX}"
        val members = file.redirects.of(owner)?.members ?: owner.simpleFunctions().toList()
        val hooks = members.filter { it.name.asString().startsWith(prefix) }
        return generateSequence(owner) { it.superClass }
            .map { level -> hooks.filter { (it.resolveFakeOverride() ?: it).parent == level } }
            .firstOrNull { it.isNotEmpty() }
            ?: hooks
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

private enum class DynamicKind { FUNCTION, GETTER, SETTER }

private class DynamicForm(
    val kind: DynamicKind,
    val receiver: IrExpression,
    val name: IrExpression?,
    val values: List<IrExpression>?,
    val problem: String = "",
)

private val IrSimpleFunction.regularParameter: IrValueParameter
    get() = parameters.first { it.kind == IrParameterKind.Regular }

private val IrSimpleFunction.signature: String
    get() = nonDispatchParameters.joinToString(
        prefix = "${name.asString().substringBefore(BuildConfig.PRIVATE_HOOK_INFIX)}(",
        postfix = ")",
    ) { it.type.erasedUpperBound.name.asString() }

private const val LIST_LITERAL = "pass the arguments as a literal 'listOf(...)': lokki resolves the member at compile time"
private val LIST_FACTORIES = setOf(FqName("kotlin.collections.listOf"), FqName("kotlin.collections.emptyList"))
