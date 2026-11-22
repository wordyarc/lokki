package io.lokki.compiler.ir.collect

import io.lokki.compiler.ir.typeKey
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.extractTypeParameters
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.types.Variance

@JvmInline
internal value class MockClassKey(val typeKey: String)

internal sealed interface MockForm {
    val keySuffix: String

    data object Mock : MockForm {
        override val keySuffix: String
            get() = ""
    }

    class Construction(val constructor: IrConstructor, val passed: List<Boolean>) : MockForm {
        override val keySuffix: String
            get() {
                val typeParameters = (constructor.constructedClass.typeParameters + constructor.typeParameters)
                    .map { it.symbol }
                return constructor.parameters.zip(passed)
                    .filter { (parameter, _) -> parameter.kind == IrParameterKind.Regular }
                    .joinToString(",", "(", ")") { (parameter, isPassed) ->
                        parameter.type.typeKey(typeParameters) + if (isPassed) "" else "="
                    }
            }
    }

    data object Delegation : MockForm {
        override val keySuffix: String
            get() = " by"
    }
}

internal class MockedTypes(val type: IrSimpleType, val interfaces: List<IrSimpleType>, val form: MockForm) {
    val key: MockClassKey = MockClassKey(
        type.typeKey(type.referencedTypeParameters) + interfaces.joinToString("") { "&" + it.typeKey(emptyList()) } +
            form.keySuffix
    )

    val classes: List<IrClass>
        get() = (listOf(type) + interfaces).map { it.mockedClass }

    val typeArguments: List<IrType>
        get() = type.referencedTypeParameters.map { it.defaultType } +
            (listOf(type) + interfaces).flatMap { it.starParameters }.map { it.instantiationArgument }
}

internal val IrType.referencedTypeParameters: List<IrTypeParameterSymbol>
    get() = LinkedHashSet<IrTypeParameterSymbol>().also(::collectTypeParameters).toList()

private fun IrType.collectTypeParameters(parameters: MutableSet<IrTypeParameterSymbol>) {
    (classifierOrNull as? IrTypeParameterSymbol)?.let(parameters::add)
    (this as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.collectTypeParameters(parameters) }
}

internal val IrSimpleType.mockedClass: IrClass
    get() = classOrFail.owner

internal val IrSimpleType.mockedClassTypeParameters: List<IrTypeParameter>
    get() = extractTypeParameters(mockedClass).take(arguments.size)

internal fun IrSimpleType.approximated(): IrSimpleType {
    val parameters = mockedClassTypeParameters
    val explicit = parameters.zip(arguments).mapNotNull { (parameter, argument) ->
        (argument as? IrTypeProjection)?.let { parameter.symbol to it.type }
    }.toMap()
    val generic = genericStars()
    val approximated = parameters.zip(arguments).map { (parameter, argument) ->
        when {
            argument is IrTypeProjection -> makeTypeProjection(argument.type, Variance.INVARIANT)
            parameter in generic -> argument
            else -> makeTypeProjection(parameter.superTypes.single().substitute(explicit), Variance.INVARIANT)
        }
    }
    return mockedClass.symbol.typeWithArguments(approximated)
}

internal fun IrSimpleType.withOwnParameters(mock: IrClass, own: Map<IrTypeParameterSymbol, IrType>): IrSimpleType {
    val parameters = mockedClassTypeParameters
    val added = ArrayList<Pair<IrTypeParameter, IrTypeParameter>>()
    val arguments = parameters.zip(arguments).map { (parameter, argument) ->
        argument.typeOrNull?.substitute(own)
            ?: mock.addTypeParameter { name = parameter.name }.also { added += parameter to it }.defaultType
    }
    val substitution = parameters.map { it.symbol }.zip(arguments).toMap()
    for ((parameter, ownParameter) in added) {
        ownParameter.superTypes = parameter.superTypes.map { it.substitute(substitution) }
    }
    return classifier.typeWith(arguments)
}

private val IrSimpleType.starParameters: List<IrTypeParameter>
    get() = mockedClassTypeParameters.filterIndexed { index, _ -> arguments[index] is IrStarProjection }

private val IrTypeParameter.instantiationArgument: IrType
    get() = superTypes.first().erasedUpperBound.symbol.starProjectedType

private fun IrSimpleType.genericStars(): List<IrTypeParameter> {
    val stars = starParameters
    val symbols = stars.map { it.symbol }.toSet()
    return stars.filter { parameter ->
        parameter.superTypes.size != 1 || parameter.superTypes.single().referencedTypeParameters.any { it in symbols }
    }
}
