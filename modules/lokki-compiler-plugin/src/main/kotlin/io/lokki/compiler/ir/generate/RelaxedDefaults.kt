package io.lokki.compiler.ir.generate

import io.lokki.compiler.CoreConstructor
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.collect.referencedTypeParameters
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.renderReadable
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

internal class RelaxedDefault(val key: String, val expression: IrBuilderWithScope.() -> IrExpression)

internal class RelaxedDefaults(lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
    private val builtIns = context.irBuiltIns

    fun of(
        member: IrSimpleFunction,
        file: IrFile,
        parameterName: (IrTypeParameter) -> String,
        child: ((IrSimpleType) -> RelaxedDefault?)?,
        suggestsChainedMocks: (IrType) -> Boolean,
    ): RelaxedDefault? {
        val type = member.returnType
        val platform = type.hasAnnotation(StandardClassIds.Annotations.FlexibleNullability)
        if (type.isMarkedNullable() && !platform) return null
        val klass = when (val classifier = type.classifierOrNull) {
            is IrTypeParameterSymbol -> return missing(file, "type parameter ${parameterName(classifier.owner)}")
            is IrClassSymbol -> classifier
            else -> return missing(file, type.render())
        }
        return when {
            klass == builtIns.unitClass -> RelaxedDefault("kotlin.Unit") { irGetObjectValue(builtIns.unitType, klass) }
            klass == builtIns.stringClass || klass == builtIns.charSequenceClass ->
                RelaxedDefault("kotlin.String") { irString("") }
            klass.defaultType.isPrimitiveType() -> RelaxedDefault(klass.owner.kotlinFqName.asString()) {
                IrConstImpl.defaultValueForType(startOffset, endOffset, klass.defaultType)
            }
            klass == builtIns.arrayClass -> array(type as IrSimpleType, file, parameterName)
            klass in builtIns.primitiveArraysToPrimitiveTypes -> RelaxedDefault(klass.owner.kotlinFqName.asString()) {
                irCallConstructor(klass.owner.constructors.single { it.parameters.size == 1 }.symbol, emptyList())
                    .apply { arguments[0] = irInt(0) }
            }
            platform && klass.owner.kotlinFqName == JAVA_LANG_VOID -> null
            else -> empty(klass.owner)
                ?: child?.invoke(type as IrSimpleType)
                ?: missing(file, type.renderReadable(parameterName), suggestsChainedMocks(type))
        }
    }

    private fun array(type: IrSimpleType, file: IrFile, parameterName: (IrTypeParameter) -> String): RelaxedDefault {
        val element = type.arguments.single().typeOrNull ?: builtIns.anyNType
        val elementClass = element.classOrNull
        val elementType = when {
            element.referencedTypeParameters.isEmpty() -> element
            elementClass != null && elementClass != builtIns.arrayClass -> elementClass.starProjectedType
            else -> return missing(file, type.renderReadable(parameterName))
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
            "kotlinx.coroutines.flow.Flow" -> symbols.emptyFlow?.let(::call)
            "java.util.Optional", "java.util.OptionalInt", "java.util.OptionalLong", "java.util.OptionalDouble",
            "java.util.stream.Stream", "java.util.stream.IntStream", "java.util.stream.LongStream",
            "java.util.stream.DoubleStream" -> call(symbols.emptyFactory(klass))
            "kotlin.collections.Iterator", "kotlin.collections.MutableIterator", "kotlin.collections.ListIterator",
            "kotlin.collections.MutableListIterator" -> listIterator()
            "kotlin.collections.MutableIterable", "kotlin.collections.MutableCollection",
            "kotlin.collections.MutableList" -> construct(symbols.javaUtilClass("ArrayList"))
            "kotlin.collections.MutableSet" -> construct(symbols.javaUtilClass("LinkedHashSet"))
            "kotlin.collections.MutableMap" -> construct(symbols.javaUtilClass("LinkedHashMap"))
            "java.util.Queue", "java.util.Deque" -> construct(symbols.javaUtilClass("ArrayDeque"))
            "java.util.SortedSet", "java.util.NavigableSet" -> construct(symbols.javaUtilClass("TreeSet"))
            "java.util.SortedMap", "java.util.NavigableMap" -> construct(symbols.javaUtilClass("TreeMap"))
            "java.util.ArrayList", "java.util.LinkedList", "java.util.ArrayDeque", "java.util.PriorityQueue",
            "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.HashMap",
            "java.util.LinkedHashMap", "java.util.TreeMap", "kotlin.collections.ArrayDeque" -> construct(klass)
            "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong" -> unsigned(klass)
            "kotlin.time.Duration" -> zeroDuration(klass)
            "java.time.Duration", "java.time.Period", "java.math.BigDecimal", "java.math.BigInteger" -> zero(klass)
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

    private fun zero(klass: IrClass): RelaxedDefault? {
        val field = klass.properties.singleOrNull { it.name.asString() == "ZERO" }?.backingField ?: return null
        return RelaxedDefault(klass.kotlinFqName.asString()) { irGetField(null, field) }
    }

    private fun construct(klass: IrClass): RelaxedDefault =
        RelaxedDefault(klass.kotlinFqName.asString()) { irEmpty(klass) }

    private fun listIterator(): RelaxedDefault {
        val list = symbols.javaUtilClass("ArrayList")
        val function = list.functions
            .single { it.name.asString() == "listIterator" && it.nonDispatchParameters.isEmpty() }
        val type = function.returnType.substitute(list.typeParameters, list.typeParameters.map { builtIns.anyNType })
        return RelaxedDefault("${list.kotlinFqName}.listIterator") {
            irInvoke(function, receiver = irEmpty(list), type = type)
        }
    }

    private fun IrBuilderWithScope.irEmpty(klass: IrClass): IrExpression {
        val typeArguments = klass.typeParameters.map { builtIns.anyNType }
        return irCallConstructor(klass.constructors.single { it.parameters.isEmpty() }.symbol, typeArguments)
            .apply { type = klass.typeWith(typeArguments) }
    }

    private fun missing(file: IrFile, description: String, mockable: Boolean = false): RelaxedDefault =
        RelaxedDefault("missing $description $mockable") {
            irInvoke(
                symbols[CoreConstructor.NO_RELAXED_DEFAULT],
                irString(description),
                irBoolean(mockable),
                irString(file.name),
            )
        }
}

private val JAVA_LANG_VOID = FqName("java.lang.Void")
