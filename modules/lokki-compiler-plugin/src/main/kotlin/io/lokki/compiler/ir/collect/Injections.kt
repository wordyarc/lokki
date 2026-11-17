package io.lokki.compiler.ir.collect

import io.lokki.compiler.LOOKUP_BY_NAME
import io.lokki.compiler.LOOKUP_BY_TYPE
import io.lokki.compiler.NOT_CLASS_PROBLEM
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.TYPE_PARAMETER_PROBLEM
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.isAccessibleOutsideHierarchy
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.renderReadable
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.Name

internal class InjectionSource(val property: IrProperty, val type: IrType, val isInjected: Boolean) {
    val name: Name
        get() = property.name
}

internal class InjectionRequest(val property: IrProperty, val annotation: IrConstructorCall)

internal class InjectionConstructor(
    val declaration: IrConstructor,
    val signature: String,
    val parameters: List<InjectionParameter>,
)

internal class InjectionParameter(val name: Name, val type: IrType, val hasDefault: Boolean)

internal class InjectionProperty(val declaration: IrProperty, val type: IrType, val isSettable: Boolean) {
    val name: Name
        get() = declaration.name
}

internal sealed interface InjectedValue {
    class Single(val source: InjectionSource) : InjectedValue

    class All(val sources: List<InjectionSource>) : InjectedValue

    data object Default : InjectedValue
}

internal sealed interface InjectionResult

internal class Injection(
    val constructor: InjectionConstructor,
    val arguments: List<InjectedValue>,
    val properties: List<Pair<InjectionProperty, InjectionSource>>,
) : InjectionResult {
    val construction: MockForm.Construction
        get() {
            val values = arguments.iterator()
            val passed = constructor.declaration.parameters.map { parameter ->
                parameter.kind != IrParameterKind.Regular || values.next() != InjectedValue.Default
            }
            return MockForm.Construction(constructor.declaration, passed)
        }
}

internal class InjectionProblem(val kind: Kind, val message: String) : InjectionResult {
    enum class Kind { UNRESOLVED, AMBIGUOUS, UNSUPPORTED }
}

internal class InjectionPlan(val order: List<IrProperty>, val results: Map<IrProperty, InjectionResult>)

internal class InjectionPlanner(private val file: IrFile, private val typeSystem: IrTypeSystemContext) {
    fun plan(owner: IrClass, requests: List<InjectionRequest>): InjectionPlan {
        val injected = requests.mapTo(HashSet()) { it.property }
        val sources = ArrayList<InjectionSource>()
        for (property in owner.declarations.filterIsInstance<IrProperty>()) {
            val getter = property.getter ?: continue
            if (getter.parameters.any { it.kind == IrParameterKind.ExtensionReceiver }) continue
            val inherited = getter.resolveFakeOverrideMaybeAbstractOrFail()
            if (property.isFakeOverride && !inherited.isVisibleFrom(file)) continue
            sources += InjectionSource(property, getter.returnType, property in injected)
        }
        val results = requests.associateTo(LinkedHashMap()) { request ->
            request.property to Resolver(sources.filter { it.property != request.property }, target(request)).resolve()
        }
        val order = ArrayList<IrProperty>()
        val pending = results.filterValues { it is Injection }.keys.toMutableList()
        while (pending.isNotEmpty()) {
            val next = pending.firstOrNull { property ->
                val dependencies = (results.getValue(property) as Injection).dependencies()
                dependencies.all { it in order || results[it] !is Injection }
            }
            if (next == null) {
                val names = pending.joinToString { "'${it.name}'" }
                for (property in pending) {
                    results[property] = InjectionProblem(
                        InjectionProblem.Kind.UNRESOLVED,
                        "the constructors of the '@InjectMocks' properties $names need each other's objects",
                    )
                }
                break
            }
            order += next
            pending -= next
        }
        return InjectionPlan(order, results)
    }

    private fun isAssignable(value: IrType, target: IrType): Boolean =
        value.makeNotNull().isSubtypeOf(target.makeNotNull(), typeSystem)

    private fun target(request: InjectionRequest): Target {
        val lookupType = (request.annotation.argument(ParameterNames.LOOKUP_TYPE) as? IrGetEnumValue)
            ?.symbol?.owner?.name?.asString()
        val byName = lookupType != LOOKUP_BY_TYPE
        val byType = lookupType != LOOKUP_BY_NAME
        val type = request.property.valueType.makeNotNull() as IrSimpleType
        val injected = type.classOrNull?.owner
        val problem = when {
            type.classifier is IrTypeParameterSymbol -> TYPE_PARAMETER_PROBLEM
            injected == null -> NOT_CLASS_PROBLEM
            injected.isInterface -> "it is an interface"
            injected.kind != ClassKind.CLASS -> NOT_CLASS_PROBLEM
            injected.modality == Modality.ABSTRACT || injected.modality == Modality.SEALED -> "it is an abstract class"
            injected.isInner -> "it is an inner class"
            else -> null
        }
        if (problem != null) return Target(byName, byType, emptyList(), emptyList(), problem)
        injected!!
        val substitution = injected.typeParameters.map { it.symbol }.zip(type.arguments.map { it.typeOrNull })
            .mapNotNull { (parameter, argument) -> argument?.let { parameter to it } }
            .toMap()
        val constructors = injected.constructors.filter { it.isAccessibleOutsideHierarchy(file) }.map { constructor ->
            val parameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }.map { parameter ->
                InjectionParameter(
                    parameter.name,
                    parameter.type.substitute(substitution),
                    parameter.defaultValue != null || parameter.varargElementType != null,
                )
            }
            val signature = parameters.joinToString(prefix = "${injected.name}(", postfix = ")") {
                "${it.name}: ${it.type.renderReadable()}"
            }
            InjectionConstructor(constructor, signature, parameters)
        }.toList()
        val properties = injected.declarations.filterIsInstance<IrProperty>().mapNotNull { injectable ->
            val getter = injectable.getter ?: return@mapNotNull null
            val setter = injectable.setter ?: return@mapNotNull null
            if (!injectable.isVar || getter.parameters.any { it.kind == IrParameterKind.ExtensionReceiver }) {
                return@mapNotNull null
            }
            val propertyType = getter.returnType.substitute(substitution)
            if (!injectable.isLateinit && !propertyType.isMarkedNullable()) return@mapNotNull null
            val settable = getter.resolveFakeOverrideMaybeAbstractOrFail().isAccessibleOutsideHierarchy(file) &&
                setter.resolveFakeOverrideMaybeAbstractOrFail().isAccessibleOutsideHierarchy(file)
            InjectionProperty(injectable, propertyType, settable)
        }
        return Target(byName, byType, constructors, properties)
    }

    private class Target(
        val byName: Boolean,
        val byType: Boolean,
        val constructors: List<InjectionConstructor>,
        val properties: List<InjectionProperty>,
        val problem: String? = null,
    )

    private sealed interface Found {
        class Value(val value: InjectedValue) : Found

        class Ambiguous(val names: List<Name>) : Found

        data object None : Found
    }

    private inner class Resolver(private val sources: List<InjectionSource>, private val target: Target) {
        fun resolve(): InjectionResult {
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
                    val subject = "parameter '${parameter.name}' of '${constructor.signature}'"
                    return ambiguous(value, subject, parameter.name)
                }
            }
            val properties = ArrayList<Pair<InjectionProperty, InjectionSource>>()
            for (property in target.properties) {
                when (val found = find(property.name, property.type, list = false)) {
                    is Found.Ambiguous -> return ambiguous(found, "its property '${property.name}'", property.name)
                    is Found.Value -> {
                        val source = (found.value as InjectedValue.Single).source
                        if (!property.isSettable) {
                            return InjectionProblem(
                                InjectionProblem.Kind.UNSUPPORTED,
                                "its property '${property.name}' would get '${source.name}', but it cannot be set " +
                                    "from this class. Make the property and its setter visible or create the " +
                                    "object yourself",
                            )
                        }
                        properties += property to source
                    }
                    Found.None -> {}
                }
            }
            return Injection(constructor, values.map { (it as Found.Value).value }, properties)
        }

        private fun find(name: Name, type: IrType, list: Boolean): Found {
            val assignable = sources.filter { isAssignable(it.type, type) }
            if (target.byName) assignable.find { it.name == name }?.let { return Found.Value(InjectedValue.Single(it)) }
            if (!target.byType) return Found.None
            val element = if (list) type.listElement() else null
            if (element != null) {
                val elements = sources.filter { isAssignable(it.type, element) }
                if (elements.isNotEmpty()) return Found.Value(InjectedValue.All(elements))
            }
            return when (assignable.size) {
                0 -> Found.None
                1 -> Found.Value(InjectedValue.Single(assignable.single()))
                else -> Found.Ambiguous(assignable.map { it.name })
            }
        }

        private fun unresolved(constructors: List<Pair<InjectionConstructor, List<Found>>>): InjectionProblem {
            if (constructors.isEmpty()) {
                val reason = "it has no constructor visible from this class"
                return InjectionProblem(InjectionProblem.Kind.UNRESOLVED, reason)
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
}

internal val InjectedValue.sources: List<InjectionSource>
    get() = when (this) {
        is InjectedValue.Single -> listOf(source)
        is InjectedValue.All -> sources
        InjectedValue.Default -> emptyList()
    }

private fun Injection.dependencies(): List<IrProperty> =
    arguments.flatMap { it.sources }.filter { it.isInjected }.map { it.property }

private fun IrType.listElement(): IrType? =
    (this as? IrSimpleType)?.takeIf { it.classFqName == StandardNames.FqNames.list }
        ?.arguments?.singleOrNull()?.typeOrNull
