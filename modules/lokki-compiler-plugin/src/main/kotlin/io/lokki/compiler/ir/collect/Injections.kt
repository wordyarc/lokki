package io.lokki.compiler.ir.collect

import io.lokki.compiler.InjectedValue
import io.lokki.compiler.Injection
import io.lokki.compiler.InjectionConstructor
import io.lokki.compiler.InjectionParameter
import io.lokki.compiler.InjectionPlan
import io.lokki.compiler.InjectionProperty
import io.lokki.compiler.InjectionSource
import io.lokki.compiler.InjectionTarget
import io.lokki.compiler.InjectionTypes
import io.lokki.compiler.ir.isAccessibleOutsideHierarchy
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.plan
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isSubtypeOf
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail
import org.jetbrains.kotlin.ir.util.substitute

internal class Injections(
    val targets: List<MockField>,
    val sources: Map<InjectionSource<IrType>, IrProperty>,
    val plan: InjectionPlan<IrType, IrConstructor, IrProperty>,
) {
    fun problemOf(field: MockField): String? =
        plan.problems[indexOf(field)]?.render(field.property.valueType.makeNotNull().render())

    fun injectionOf(field: MockField): Injection<IrType, IrConstructor, IrProperty>? = plan.injections[indexOf(field)]

    fun constructionOf(field: MockField): MockForm.Construction? {
        val injection = injectionOf(field) ?: return null
        val values = injection.arguments.iterator()
        val passed = injection.constructor.declaration.parameters.map { parameter ->
            parameter.kind != IrParameterKind.Regular || values.next() != InjectedValue.Default
        }
        return MockForm.Construction(injection.constructor.declaration, passed)
    }

    private fun indexOf(field: MockField): Int = targets.indexOfFirst { it.property == field.property }
}

internal var IrModuleFragment.builtIns: IrBuiltIns? by irAttribute(copyByDefault = false)

private var IrClass.cachedInjections: Injections? by irAttribute(copyByDefault = false)

internal fun IrClass.injections(file: IrFile): Injections =
    cachedInjections ?: planInjections(file).also { cachedInjections = it }

private fun IrClass.planInjections(file: IrFile): Injections {
    val targets = mockFields.filter { it.inject != null && it.declarationReason(file) == null }
    val sources = LinkedHashMap<InjectionSource<IrType>, IrProperty>()
    if (targets.isNotEmpty()) {
        for (property in declarations.filterIsInstance<IrProperty>()) {
            val getter = property.getter ?: continue
            if (getter.parameters.any { it.kind == IrParameterKind.ExtensionReceiver }) continue
            val inherited = getter.resolveFakeOverrideMaybeAbstractOrFail()
            if (property.isFakeOverride && !inherited.isVisibleFrom(file)) continue
            val target = targets.indexOfFirst { it.property == property }.takeIf { it >= 0 }
            sources[InjectionSource(property.name, getter.returnType, target)] = property
        }
    }
    val types = IrInjectionTypes(file.module.builtIns!!)
    return Injections(targets, sources, types.plan(sources.keys.toList(), targets.map { it.injectionTarget(file) }))
}

private class IrInjectionTypes(builtIns: IrBuiltIns) : InjectionTypes<IrType> {
    private val typeSystem = IrTypeSystemContextImpl(builtIns)

    override fun isAssignable(value: IrType, target: IrType): Boolean =
        value.makeNotNull().isSubtypeOf(target.makeNotNull(), typeSystem)

    override fun listElement(type: IrType): IrType? =
        (type as? IrSimpleType)?.takeIf { it.classFqName == StandardNames.FqNames.list }
            ?.arguments?.singleOrNull()?.typeOrNull
}

private fun MockField.injectionTarget(file: IrFile): InjectionTarget<IrType, IrConstructor, IrProperty> {
    val lookupType = (inject!!.argument("lookupType") as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
    val byName = lookupType != "BY_TYPE"
    val byType = lookupType != "BY_NAME"
    val type = property.valueType.makeNotNull() as IrSimpleType
    val injected = type.classOrNull?.owner
    val problem = when {
        type.classifier is IrTypeParameterSymbol -> "it is a type parameter"
        injected == null -> "it is not a class"
        injected.isInterface -> "it is an interface"
        injected.kind != ClassKind.CLASS -> "it is not a class"
        injected.modality == Modality.ABSTRACT || injected.modality == Modality.SEALED -> "it is an abstract class"
        injected.isInner -> "it is an inner class"
        else -> null
    }
    if (problem != null) return InjectionTarget(property.name, byName, byType, emptyList(), emptyList(), problem)
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
            "${it.name}: ${it.type.render()}"
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
        InjectionProperty(injectable, injectable.name, propertyType, settable)
    }
    return InjectionTarget(property.name, byName, byType, constructors, properties)
}
