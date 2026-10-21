package io.lokki.compiler.ir.generate

import io.lokki.compiler.ir.LokkiSymbols
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance

internal class RelaxedDefault(val key: String, val expression: IrBuilderWithScope.() -> IrExpression)

internal class RelaxedDefaults(private val context: IrPluginContext, private val symbols: LokkiSymbols) {
    private val builtIns = context.irBuiltIns

    fun of(
        type: IrType,
        parameterName: (IrTypeParameter) -> String,
        child: ((IrSimpleType) -> RelaxedDefault?)?,
        suggestsChainedMocks: Boolean,
    ): RelaxedDefault? {
        val platform = type.hasAnnotation(StandardClassIds.Annotations.FlexibleNullability)
        if (type.isMarkedNullable() && !platform) return null
        val klass = when (val classifier = type.classifierOrNull) {
            is IrTypeParameterSymbol -> return missing("type parameter ${parameterName(classifier.owner)}")
            is IrClassSymbol -> classifier
            else -> return missing(type.render())
        }
        return when {
            klass == builtIns.unitClass -> RelaxedDefault("kotlin.Unit") { irGetObjectValue(builtIns.unitType, klass) }
            klass == builtIns.stringClass -> RelaxedDefault("kotlin.String") { irString("") }
            klass.defaultType.isPrimitiveType() -> RelaxedDefault(klass.owner.kotlinFqName.asString()) {
                IrConstImpl.defaultValueForType(startOffset, endOffset, klass.defaultType)
            }
            klass == builtIns.arrayClass -> array(type as IrSimpleType, parameterName)
            klass in builtIns.primitiveArraysToPrimitiveTypes -> RelaxedDefault(klass.owner.kotlinFqName.asString()) {
                irCallConstructor(klass.owner.constructors.single { it.parameters.size == 1 }.symbol, emptyList())
                    .apply { arguments[0] = irInt(0) }
            }
            else -> empty(klass.owner)
                ?: child?.invoke(type as IrSimpleType)
                ?: missing(type.description(parameterName), suggestsChainedMocks && klass.owner.isOpen)
        }
    }

    private fun array(type: IrSimpleType, parameterName: (IrTypeParameter) -> String): RelaxedDefault {
        val element = type.arguments.single().typeOrNull ?: builtIns.anyNType
        val elementClass = element.classOrNull
        val elementType = when {
            !element.mentionsTypeParameters() -> element
            elementClass != null && elementClass != builtIns.arrayClass -> elementClass.starProjectedType
            else -> return missing(type.description(parameterName))
        }
        return RelaxedDefault("kotlin.Array<${elementType.render()}>") {
            irCall(builtIns.arrayOfNulls, builtIns.arrayClass.typeWith(elementType.makeNullable()), listOf(elementType))
                .apply { arguments[0] = irInt(0) }
        }
    }

    private fun empty(klass: IrClass): RelaxedDefault? =
        when (klass.kotlinFqName.asString()) {
            "kotlin.collections.Iterable", "kotlin.collections.Collection", "kotlin.collections.List" ->
                call(symbols.emptyList)
            "kotlin.collections.Set" -> call(symbols.emptySet)
            "kotlin.collections.Map" -> call(symbols.emptyMap)
            "kotlin.sequences.Sequence" -> call(symbols.emptySequence)
            "java.util.Optional" -> call(symbols.optionalEmpty)
            "kotlin.collections.MutableIterable", "kotlin.collections.MutableCollection",
            "kotlin.collections.MutableList", "java.util.ArrayList" -> construct("ArrayList")
            "kotlin.collections.MutableSet", "java.util.LinkedHashSet" -> construct("LinkedHashSet")
            "java.util.HashSet" -> construct("HashSet")
            "kotlin.collections.MutableMap", "java.util.LinkedHashMap" -> construct("LinkedHashMap")
            "java.util.HashMap" -> construct("HashMap")
            else -> null
        }

    private fun call(function: IrSimpleFunction): RelaxedDefault {
        val typeArguments = function.typeParameters.map { builtIns.anyNType }
        val type = function.returnType.substitute(function.typeParameters, typeArguments)
        return RelaxedDefault(function.name.asString()) { irCall(function.symbol, type, typeArguments) }
    }

    private fun construct(name: String): RelaxedDefault {
        val klass = symbols.javaUtilClass(name)
        val typeArguments = klass.typeParameters.map { builtIns.anyNType }
        return RelaxedDefault(klass.kotlinFqName.asString()) {
            irCallConstructor(klass.constructors.single { it.parameters.isEmpty() }.symbol, typeArguments)
                .apply { type = klass.typeWith(typeArguments) }
        }
    }

    private fun missing(description: String, mockable: Boolean = false): RelaxedDefault =
        RelaxedDefault("missing $description $mockable") {
            irCallConstructor(symbols.noRelaxedDefault.constructors.single().symbol, emptyList()).apply {
                arguments[0] = irString(description)
                arguments[1] = irBoolean(mockable)
            }
        }
}

private val IrClass.isOpen: Boolean
    get() = modality != Modality.FINAL && modality != Modality.SEALED && when (kind) {
        ClassKind.INTERFACE -> true
        ClassKind.CLASS -> constructors.any { !DescriptorVisibilities.isPrivate(it.visibility) }
        else -> false
    }

private fun IrType.mentionsTypeParameters(): Boolean =
    classifierOrNull is IrTypeParameterSymbol ||
        (this as? IrSimpleType)?.arguments.orEmpty().any { it.typeOrNull?.mentionsTypeParameters() == true }

private fun IrType.description(parameterName: (IrTypeParameter) -> String): String =
    when (val classifier = classifierOrNull) {
        is IrTypeParameterSymbol -> parameterName(classifier.owner)
        is IrClassSymbol -> buildString {
            generateSequence(classifier.owner) { it.parent as? IrClass }.toList().asReversed()
                .joinTo(this, ".") { it.name.asString() }
            val arguments = (this@description as? IrSimpleType)?.arguments.orEmpty()
            if (arguments.isNotEmpty()) {
                arguments.joinTo(this, ", ", "<", ">") { argument ->
                    when (argument) {
                        is IrStarProjection -> "*"
                        is IrTypeProjection -> argument.variance.prefix + argument.type.description(parameterName)
                    }
                }
            }
            if (isMarkedNullable() && !hasAnnotation(StandardClassIds.Annotations.FlexibleNullability)) append('?')
        }
        else -> render()
    }

private val Variance.prefix: String
    get() = if (this == Variance.INVARIANT) "" else "$label "
