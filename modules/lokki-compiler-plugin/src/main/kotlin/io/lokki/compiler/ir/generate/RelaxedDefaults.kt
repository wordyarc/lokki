package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.effectiveModality
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.renderReadable
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultValueForType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.name.StandardClassIds

internal class RelaxedDefault(val key: String, val expression: IrBuilderWithScope.() -> IrExpression)

internal class RelaxedDefaults(lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
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
            is IrTypeParameterSymbol -> return when {
                platform && classifier.owner.parent is IrFunction -> null
                else -> missing("type parameter ${parameterName(classifier.owner)}")
            }
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
                ?: missing(type.renderReadable(parameterName), suggestsChainedMocks && klass.owner.isOpen)
        }
    }

    private fun array(type: IrSimpleType, parameterName: (IrTypeParameter) -> String): RelaxedDefault {
        val element = type.arguments.single().typeOrNull ?: builtIns.anyNType
        val elementClass = element.classOrNull
        val elementType = when {
            element.referencedTypeParameters.isEmpty() -> element
            elementClass != null && elementClass != builtIns.arrayClass -> elementClass.starProjectedType
            else -> return missing(type.renderReadable(parameterName))
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
            "java.util.Optional", "java.util.OptionalInt", "java.util.OptionalLong", "java.util.OptionalDouble" ->
                call(symbols.optionalEmpty(klass))
            "kotlin.collections.MutableIterable", "kotlin.collections.MutableCollection",
            "kotlin.collections.MutableList", "java.util.ArrayList" -> construct("ArrayList")
            "kotlin.collections.MutableSet", "java.util.LinkedHashSet" -> construct("LinkedHashSet")
            "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong" -> unsigned(klass)
            "kotlin.time.Duration" -> zeroDuration(klass)
            "kotlin.time.Instant", "kotlinx.datetime.Instant" -> epoch(klass)
            "java.util.HashSet" -> construct("HashSet")
            "kotlin.collections.MutableMap", "java.util.LinkedHashMap" -> construct("LinkedHashMap")
            "java.util.HashMap" -> construct("HashMap")
            else -> null
        }

    private fun call(function: IrSimpleFunction): RelaxedDefault {
        val typeArguments = function.typeParameters.map { builtIns.anyNType }
        val type = function.returnType.substitute(function.typeParameters, typeArguments)
        return RelaxedDefault(function.kotlinFqName.asString()) { irCall(function.symbol, type, typeArguments) }
    }

    private fun unsigned(klass: IrClass): RelaxedDefault? {
        val constructor = klass.primaryConstructor ?: return null
        val value = constructor.parameters.singleOrNull()?.type ?: return null
        return RelaxedDefault(klass.kotlinFqName.asString()) {
            irInvoke(constructor, IrConstImpl.defaultValueForType(startOffset, endOffset, value))
        }
    }

    private fun zeroDuration(klass: IrClass): RelaxedDefault? {
        val companion = klass.companionObject() ?: return null
        val zero = companion.properties.singleOrNull { it.name.asString() == "ZERO" }?.getter ?: return null
        return RelaxedDefault(klass.kotlinFqName.asString()) { irInvoke(zero, receiver = irGetObject(companion)) }
    }

    private fun epoch(klass: IrClass): RelaxedDefault? {
        val companion = klass.companionObject() ?: return null
        val fromMillis = companion.functions.singleOrNull { function ->
            function.name.asString() == "fromEpochMilliseconds" &&
                function.nonDispatchParameters.singleOrNull()?.type?.isLong() == true
        } ?: return null
        return RelaxedDefault(klass.kotlinFqName.asString()) {
            irInvoke(fromMillis, irLong(0), receiver = irGetObject(companion))
        }
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
            irInvoke(symbols[CoreConstructor.NO_RELAXED_DEFAULT], irString(description), irBoolean(mockable))
        }
}

private val IrClass.isOpen: Boolean
    get() = effectiveModality != Modality.FINAL && effectiveModality != Modality.SEALED && when (kind) {
        ClassKind.INTERFACE -> true
        ClassKind.CLASS -> constructors.any { !DescriptorVisibilities.isPrivate(it.visibility) }
        else -> false
    }
