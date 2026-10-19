package io.lokki.compiler.ir.collect

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.isMissing
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.missingClasses
import io.lokki.compiler.ir.typeKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.extractTypeParameters
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getAllSuperclasses
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.name.FqName

internal data class MockClassKey(val typeKey: String, val file: IrFile)

internal val IrCall.mockedType: IrType
    get() = typeArguments.single()!!

internal fun IrCall.mockableType(file: IrFile): IrSimpleType? =
    if (intrinsicKind != IntrinsicKind.MOCK || mockedType.notMockableReason(file) != null) null
    else (mockedType as IrSimpleType).approximated()

internal fun IrSimpleType.mockClassKey(file: IrFile): MockClassKey =
    MockClassKey(typeKey(referencedTypeParameters), file)

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

internal fun IrType.notMockableReason(file: IrFile): String? {
    val type = classOrNull?.owner
        ?: return "The mocked type must be known at compile time, but '${render()}' is not a class"
    val name = type.kotlinFqName
    return when {
        type.kind == ClassKind.OBJECT -> "'$name' is an object and cannot be mocked"
        type.kind == ClassKind.ENUM_CLASS || type.kind == ClassKind.ENUM_ENTRY ->
            "'$name' is an enum class and cannot be mocked"
        type.kind == ClassKind.ANNOTATION_CLASS -> "'$name' is an annotation class and cannot be mocked"
        type.modality == Modality.SEALED -> "'$name' is sealed and cannot be mocked"
        type.modality == Modality.FINAL -> "'$name' is final and cannot be mocked"
        (this as IrSimpleType).approximated() == null ->
            "'${render()}' has a star projection that cannot be replaced with the upper bound of its type parameter"
        type.kind == ClassKind.CLASS && type.constructors.none { it.isVisibleFrom(file) } ->
            "'$name' has no constructor visible to the mock"
        else -> type.getAllSuperclasses().filter { it.isMissing }.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "'$name' has supertypes missing from the classpath: ") { it.kotlinFqName.asString() }
            ?: type.hiddenAbstractMembers(file).takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "'$name' has abstract members that the mock cannot implement: ")
            ?: type.membersWithMissingClasses().takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "'$name' or its supertypes declare members that refer to classes missing from the classpath: ",
            )
    }
}

private fun IrSimpleType.approximated(): IrSimpleType? {
    val parameters = mockedClassTypeParameters
    val stars = parameters.filterIndexed { index, _ -> arguments[index] is IrStarProjection }.map { it.symbol }.toSet()
    val explicit = parameters.zip(arguments).mapNotNull { (parameter, argument) ->
        (argument as? IrTypeProjection)?.let { parameter.symbol to it.type }
    }.toMap()
    val approximated = parameters.zip(arguments).map { (parameter, argument) ->
        when (argument) {
            is IrTypeProjection -> argument.type
            is IrStarProjection -> parameter.superTypes.singleOrNull()
                ?.takeUnless { it.mentions(stars) }
                ?.substitute(explicit)
                ?: return null
        }
    }
    return mockedClass.typeWith(approximated)
}

private fun IrType.mentions(parameters: Set<IrTypeParameterSymbol>): Boolean =
    classifierOrNull in parameters ||
        (this as? IrSimpleType)?.arguments.orEmpty().any { it.typeOrNull?.mentions(parameters) == true }

private fun IrClass.hiddenAbstractMembers(file: IrFile): List<String> {
    val declared = declarations.filterIsInstance<IrOverridableMember>()
        .filter { it.modality == Modality.ABSTRACT && !it.isVisibleFrom(file) }
    return (declared + uninheritedAbstractMembers()).map { it.name.asString() }.distinct()
}

private fun IrClass.uninheritedAbstractMembers(): List<IrOverridableMember> {
    val superclasses = generateSequence(this) { type ->
        type.superTypes.firstNotNullOfOrNull { supertype ->
            supertype.classOrNull?.owner?.takeIf { it.kind == ClassKind.CLASS }
        }
    }.toList()
    return superclasses.withIndex()
        .filter { (index, owner) -> superclasses.take(index).any { it.packageFqName != owner.packageFqName } }
        .flatMap { (_, owner) -> owner.declarations.filterIsInstance<IrOverridableMember>() }
        .filter { it.modality == Modality.ABSTRACT && it.visibility == JavaDescriptorVisibilities.PACKAGE_VISIBILITY }
}

private val IrClass.packageFqName: FqName
    get() = getPackageFragment().packageFqName

private fun IrClass.membersWithMissingClasses(): List<String> =
    (listOf(this) + getAllSuperclasses()).flatMap { it.declarations }.flatMap { member ->
        when (member) {
            is IrSimpleFunction -> listOf(member)
            is IrProperty -> listOfNotNull(member.getter, member.setter)
            else -> emptyList()
        }
    }.filterNot { it.isFakeOverride }.mapNotNull { member ->
        val missing = member.missingClasses.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val name = member.correspondingPropertySymbol?.owner?.name ?: member.name
        missing.joinToString(prefix = "$name (", postfix = ")") { it.kotlinFqName.asString() }
    }.distinct()
