package io.lokki.compiler

import org.jetbrains.kotlin.name.Name

internal interface InjectionTypes<T> {
    fun isAssignable(value: T, target: T): Boolean

    fun listElement(type: T): T?
}

internal class InjectionSource<T>(val name: Name, val type: T, val target: Int?)

internal class InjectionTarget<T, C, P>(
    val name: Name,
    val byName: Boolean,
    val byType: Boolean,
    val constructors: List<InjectionConstructor<T, C>>,
    val properties: List<InjectionProperty<T, P>>,
    val problem: String? = null,
)

internal class InjectionConstructor<T, C>(
    val declaration: C,
    val signature: String,
    val parameters: List<InjectionParameter<T>>,
)

internal class InjectionParameter<T>(val name: Name, val type: T, val hasDefault: Boolean)

internal class InjectionProperty<T, P>(val declaration: P, val name: Name, val type: T, val isSettable: Boolean)

internal sealed interface InjectedValue<out T> {
    class Single<T>(val source: InjectionSource<T>) : InjectedValue<T>

    class All<T>(val sources: List<InjectionSource<T>>) : InjectedValue<T>

    data object Default : InjectedValue<Nothing>
}

internal sealed interface InjectionResult<out T, out C, out P>

internal class Injection<T, C, P>(
    val constructor: InjectionConstructor<T, C>,
    val arguments: List<InjectedValue<T>>,
    val properties: List<Pair<InjectionProperty<T, P>, InjectionSource<T>>>,
) : InjectionResult<T, C, P>

internal class InjectionProblem(val kind: Kind, val message: String) : InjectionResult<Nothing, Nothing, Nothing> {
    enum class Kind { UNRESOLVED, AMBIGUOUS, UNSUPPORTED }

    fun render(type: String): String =
        when (kind) {
            Kind.UNRESOLVED -> "'@InjectMocks' cannot create '$type': $message"
            Kind.AMBIGUOUS -> "'@InjectMocks' cannot choose how to create '$type': $message"
            Kind.UNSUPPORTED -> "'@InjectMocks' cannot inject '$type': $message"
        }
}

internal class InjectionPlan<T, C, P>(
    val order: List<Int>,
    val injections: Map<Int, Injection<T, C, P>>,
    val problems: Map<Int, InjectionProblem>,
)

internal fun <T, C, P> InjectionTypes<T>.plan(
    sources: List<InjectionSource<T>>,
    targets: List<InjectionTarget<T, C, P>>,
): InjectionPlan<T, C, P> {
    val injections = LinkedHashMap<Int, Injection<T, C, P>>()
    val problems = HashMap<Int, InjectionProblem>()
    for ((index, target) in targets.withIndex()) {
        when (val result = Resolver(this, sources.filter { it.target != index }, target).resolve()) {
            is Injection -> injections[index] = result
            is InjectionProblem -> problems[index] = result
        }
    }
    val order = ArrayList<Int>()
    val pending = injections.keys.toMutableList()
    while (pending.isNotEmpty()) {
        val next = pending.firstOrNull { index ->
            injections.getValue(index).dependencies().all { it in order || it !in injections }
        }
        if (next == null) {
            val names = pending.joinToString { "'${targets[it].name}'" }
            for (index in pending) {
                injections.remove(index)
                problems[index] = InjectionProblem(
                    InjectionProblem.Kind.UNRESOLVED,
                    "the constructors of the '@InjectMocks' properties $names need each other's objects",
                )
            }
            break
        }
        order += next
        pending -= next
    }
    return InjectionPlan(order, injections, problems)
}

internal val InjectedValue<*>.sources: List<InjectionSource<*>>
    get() = when (this) {
        is InjectedValue.Single -> listOf(source)
        is InjectedValue.All -> sources
        InjectedValue.Default -> emptyList()
    }

private fun Injection<*, *, *>.dependencies(): List<Int> = arguments.flatMap { it.sources }.mapNotNull { it.target }

private sealed interface Found<out T> {
    class Value<T>(val value: InjectedValue<T>) : Found<T>

    class Ambiguous(val names: List<Name>) : Found<Nothing>

    data object None : Found<Nothing>
}

private class Resolver<T, C, P>(
    private val types: InjectionTypes<T>,
    private val sources: List<InjectionSource<T>>,
    private val target: InjectionTarget<T, C, P>,
) {
    fun resolve(): InjectionResult<T, C, P> {
        target.problem?.let { return InjectionProblem(InjectionProblem.Kind.UNRESOLVED, it) }
        val constructors = target.constructors.map { constructor ->
            constructor to constructor.parameters.map { parameter ->
                when (val found = find(parameter.name, parameter.type, list = true)) {
                    Found.None -> if (parameter.hasDefault) Found.Value(InjectedValue.Default) else found
                    else -> found
                }
            }
        }
        val callable = constructors.filter { (_, values) -> Found.None !in values }
        if (callable.isEmpty()) return unresolved(constructors)
        val longest = callable.maxOf { (constructor, _) -> constructor.parameters.size }
        val best = callable.filter { (constructor, _) -> constructor.parameters.size == longest }
        if (best.size > 1) {
            return InjectionProblem(
                InjectionProblem.Kind.AMBIGUOUS,
                "several of its constructors with the most parameters can be called: " +
                    best.joinToString { (constructor, _) -> "'${constructor.signature}'" },
            )
        }
        val (constructor, values) = best.single()
        for ((parameter, value) in constructor.parameters.zip(values)) {
            if (value is Found.Ambiguous) {
                return ambiguous(value, "parameter '${parameter.name}' of '${constructor.signature}'", parameter.name)
            }
        }
        val properties = ArrayList<Pair<InjectionProperty<T, P>, InjectionSource<T>>>()
        for (property in target.properties) {
            when (val found = find(property.name, property.type, list = false)) {
                is Found.Ambiguous -> return ambiguous(found, "its property '${property.name}'", property.name)
                is Found.Value -> {
                    val source = (found.value as InjectedValue.Single).source
                    if (!property.isSettable) {
                        return InjectionProblem(
                            InjectionProblem.Kind.UNSUPPORTED,
                            "its property '${property.name}' would get '${source.name}', but it cannot be set from " +
                                "this class. Make the property and its setter visible or create the object yourself",
                        )
                    }
                    properties += property to source
                }
                Found.None -> {}
            }
        }
        return Injection(constructor, values.map { (it as Found.Value).value }, properties)
    }

    private fun find(name: Name, type: T, list: Boolean): Found<T> {
        val assignable = sources.filter { types.isAssignable(it.type, type) }
        if (target.byName) assignable.find { it.name == name }?.let { return Found.Value(InjectedValue.Single(it)) }
        if (!target.byType) return Found.None
        val element = if (list) types.listElement(type) else null
        if (element != null) {
            val elements = sources.filter { types.isAssignable(it.type, element) }
            if (elements.isNotEmpty()) return Found.Value(InjectedValue.All(elements))
        }
        return when (assignable.size) {
            0 -> Found.None
            1 -> Found.Value(InjectedValue.Single(assignable.single()))
            else -> Found.Ambiguous(assignable.map { it.name })
        }
    }

    private fun unresolved(constructors: List<Pair<InjectionConstructor<T, C>, List<Found<T>>>>): InjectionProblem {
        if (constructors.isEmpty()) {
            return InjectionProblem(InjectionProblem.Kind.UNRESOLVED, "it has no constructor visible from this class")
        }
        val missing = constructors.joinToString("; ") { (constructor, values) ->
            val names = constructor.parameters.zip(values).filter { (_, value) -> value == Found.None }
                .joinToString { (parameter, _) -> "'${parameter.name}'" }
            "'${constructor.signature}' has no property for $names"
        }
        return InjectionProblem(
            InjectionProblem.Kind.UNRESOLVED,
            "no constructor can be called with the properties of this class: $missing",
        )
    }

    private fun ambiguous(found: Found.Ambiguous, subject: String, name: Name): InjectionProblem =
        InjectionProblem(
            InjectionProblem.Kind.AMBIGUOUS,
            "$subject matches several properties by type: ${found.names.joinToString { "'$it'" }}. " +
                "Name one of them '$name' or create the object yourself",
        )
}
