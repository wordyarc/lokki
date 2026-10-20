package io.lokki.compiler.ir.collect

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.inaccessibleClasses
import io.lokki.compiler.ir.isMissing
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.missingClasses
import io.lokki.compiler.ir.packageFqName
import io.lokki.compiler.ir.typeKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
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
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail
import org.jetbrains.kotlin.ir.util.substitute
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.name.FqName

internal data class MockClassKey(val typeKey: String, val file: IrFile)

internal class MockedTypes(val type: IrSimpleType, val interfaces: List<IrSimpleType>) {
    val classes: List<IrClass>
        get() = (listOf(type) + interfaces).map { it.mockedClass }
}

internal val IrCall.mockedType: IrType
    get() = typeArguments.single()!!

internal fun IrCall.mockedTypes(file: IrFile): MockedTypes? {
    if (intrinsicKind != IntrinsicKind.MOCK || notMockableReason(file) != null) return null
    val type = (mockedType as IrSimpleType).approximated()!!
    val interfaces = moreInterfaces()!!.map { (it as IrSimpleType).approximated()!! }.distinctBy { it.mockedClass }
    val all = listOf(type) + interfaces
    val added = interfaces.filter { extra ->
        all.none { other ->
            other !== extra &&
                (other.mockedClass == extra.mockedClass || extra.mockedClass in other.mockedClass.getAllSuperclasses())
        }
    }
    return MockedTypes(type, added.sortedBy { it.typeKey(emptyList()) })
}

internal fun IrCall.notMockableReason(file: IrFile): String? {
    mockedType.notMockableReason(file)?.let { return it }
    val interfaces = moreInterfaces()
        ?: return "'moreInterfaces' takes only class literals of interfaces, such as 'Closeable::class'"
    for (type in interfaces) {
        val extra = type.classOrNull?.owner
        if (extra != null && !extra.isInterface) {
            return "'${extra.kotlinFqName}' is not an interface: 'moreInterfaces' takes only interfaces"
        }
        type.notMockableReason(file)?.let { return it }
    }
    return null
}

internal fun MockedTypes.mockClassKey(file: IrFile): MockClassKey {
    val interfaceKeys = interfaces.joinToString("") { "&" + it.typeKey(emptyList()) }
    return MockClassKey(type.typeKey(type.referencedTypeParameters) + interfaceKeys, file)
}

private fun IrCall.moreInterfaces(): List<IrType>? {
    val parameter = symbol.owner.parameters.find { it.name.asString() == MORE_INTERFACES } ?: return emptyList()
    return when (val argument = arguments[parameter.indexInParameters]?.unwrapTemporary()) {
        null -> emptyList()
        is IrVararg -> argument.classLiterals()
        else -> null
    }
}

private fun IrVararg.classLiterals(): List<IrType>? =
    elements.flatMap { element ->
        when (element) {
            is IrClassReference -> listOf(element.classType)
            is IrSpreadElement -> {
                val array = (element.expression.unwrapTemporary() as? IrCall)
                    ?.takeIf { it.symbol.owner.kotlinFqName == ARRAY_OF }
                (array?.arguments?.singleOrNull() as? IrVararg)?.classLiterals() ?: return null
            }
            else -> return null
        }
    }

private fun IrExpression.unwrapTemporary(): IrExpression {
    val variable = (this as? IrGetValue)?.symbol?.owner as? IrVariable
    return variable?.takeIf { it.origin == IrDeclarationOrigin.IR_TEMPORARY_VARIABLE && !it.isVar }?.initializer ?: this
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
            ?: type.membersWithInaccessibleClasses(file).takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "'$name' has members that refer to package-private classes of another package: ",
            )
    }
}

private val ARRAY_OF = FqName("kotlin.arrayOf")
private const val MORE_INTERFACES = "moreInterfaces"

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

private fun IrClass.membersWithMissingClasses(): List<String> =
    (listOf(this) + getAllSuperclasses()).flatMap { it.memberFunctions }
        .filterNot { it.isFakeOverride }
        .mapNotNull { member -> member.withClasses(member.missingClasses) }
        .distinct()

private fun IrClass.membersWithInaccessibleClasses(file: IrFile): List<String> =
    memberFunctions
        .filter { it.modality != Modality.FINAL && it.resolveFakeOverrideMaybeAbstractOrFail().isVisibleFrom(file) }
        .mapNotNull { member -> member.withClasses(member.inaccessibleClasses(file)) }
        .distinct()

private val IrClass.memberFunctions: List<IrSimpleFunction>
    get() = declarations.flatMap { member ->
        when (member) {
            is IrSimpleFunction -> listOf(member)
            is IrProperty -> listOfNotNull(member.getter, member.setter)
            else -> emptyList()
        }
    }

private fun IrSimpleFunction.withClasses(classes: List<IrClass>): String? {
    if (classes.isEmpty()) return null
    val name = correspondingPropertySymbol?.owner?.name ?: name
    return classes.joinToString(prefix = "$name (", postfix = ")") { it.kotlinFqName.asString() }
}
