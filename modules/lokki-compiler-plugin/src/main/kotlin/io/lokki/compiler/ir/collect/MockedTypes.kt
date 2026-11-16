package io.lokki.compiler.ir.collect

import io.lokki.compiler.ir.typeKey
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
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.substitute

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
        get() = type.referencedTypeParameters.map { it.defaultType }
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

internal fun IrSimpleType.approximated(): IrSimpleType? {
    if (irreplaceableStar() != null) return null
    val parameters = mockedClassTypeParameters
    val explicit = parameters.zip(arguments).mapNotNull { (parameter, argument) ->
        (argument as? IrTypeProjection)?.let { parameter.symbol to it.type }
    }.toMap()
    val approximated = parameters.zip(arguments).map { (parameter, argument) ->
        when (argument) {
            is IrTypeProjection -> argument.type
            is IrStarProjection -> parameter.superTypes.single().substitute(explicit)
        }
    }
    return mockedClass.typeWith(approximated)
}

internal fun IrSimpleType.irreplaceableStar(): IrTypeParameter? {
    val stars = mockedClassTypeParameters.filterIndexed { index, _ -> arguments[index] is IrStarProjection }
    val symbols = stars.map { it.symbol }.toSet()
    return stars.firstOrNull { parameter ->
        parameter.superTypes.size != 1 || parameter.superTypes.single().referencedTypeParameters.any { it in symbols }
    }
}
