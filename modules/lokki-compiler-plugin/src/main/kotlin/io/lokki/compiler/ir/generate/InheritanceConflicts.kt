package io.lokki.compiler.ir.generate

import io.lokki.compiler.ir.renderReadable
import io.lokki.compiler.ir.typeKey
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.getAllSubstitutedSupertypes
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.substitute

internal fun IrClass.inheritanceConflict(): String? {
    val supertypes = getAllSubstitutedSupertypes(this).sortedBy { it.typeKey(emptyList()) }.groupBy { it.classOrFail }
    for ((supertype, types) in supertypes) {
        val distinct = types.distinctBy { it.typeKey(emptyList()) }
        if (distinct.size > 1) {
            return "it would inherit '${supertype.owner.name}' with different type arguments: " +
                distinct.joinToString(" and ") { "'${it.renderReadable()}'" }
        }
    }
    val arguments = supertypes.mapValues { (supertype, types) ->
        supertype.owner.typeParameters.map { it.symbol }.zip(types.first().arguments.map { it.typeOrFail }).toMap()
    }
    for (member in declarations) {
        val conflict = when (member) {
            is IrSimpleFunction -> member.conflict(arguments) { it.returnType }
            is IrProperty -> member.conflict(arguments) { it.getter?.returnType }
            else -> null
        } ?: continue
        val name = (member as IrDeclarationWithName).name
        val owners = conflict.joinToString(" and ") { "'${it.parentAsClass.name}'" }
        return "its member '$name' has incompatible types in $owners"
    }
    return null
}

private fun <S : IrOverridableDeclaration<*>> S.conflict(
    arguments: Map<IrClassSymbol, Map<IrTypeParameterSymbol, IrType>>,
    type: (S) -> IrType?,
): List<IrOverridableDeclaration<*>>? {
    @Suppress("UNCHECKED_CAST")
    val overridden = overriddenSymbols.map { it.owner as S }
    if (overridden.size < 2) return null
    val ownType = type(this) ?: return null
    val own = ownType.erasedUpperBound
    for (base in overridden) {
        val substitution = arguments[base.parentAsClass.symbol].orEmpty() + base.typeParameterSubstitution(this)
        val baseType = type(base)?.substitute(substitution)?.erasedUpperBound ?: continue
        val compatible = when {
            base is IrProperty && base.isVar -> own == baseType
            else -> ownType.isNothing() || own.isSubclassOf(baseType)
        }
        if (!compatible) return listOf(overridden.first { it !== base }, base)
    }
    return null
}

private fun IrOverridableDeclaration<*>.typeParameterSubstitution(
    fakeOverride: IrOverridableDeclaration<*>,
): Map<IrTypeParameterSymbol, IrType> {
    val from = (this as? IrSimpleFunction)?.typeParameters.orEmpty()
    val to = (fakeOverride as? IrSimpleFunction)?.typeParameters.orEmpty()
    return from.zip(to).associate { (parameter, own) -> parameter.symbol to own.defaultType }
}
