package io.lokki.compiler.fir

import io.lokki.compiler.declarationKindProblem
import io.lokki.compiler.memberWithClasses
import io.lokki.compiler.missingMemberClassesProblem
import io.lokki.compiler.missingSupertypesProblem
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.toTypeParameterSymbol
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.type

context(context: CheckerContext)
internal fun FirRegularClassSymbol.mockabilityProblem(): String? =
    context.session.lokkiEnvironment.mockabilityProblems.getValue(this, context)

context(context: CheckerContext)
internal fun FirRegularClassSymbol.findMockabilityProblem(): String? =
    declarationKindProblem(classKind, modality, suggestsMockable = isFromFriendKotlinModule && !isInlineOrValue)
        ?: missingSupertypes().takeIf { it.isNotEmpty() }?.let(::missingSupertypesProblem)
        ?: membersWithMissingClasses().takeIf { it.isNotEmpty() }?.let(::missingMemberClassesProblem)

context(context: CheckerContext)
private fun FirRegularClassSymbol.missingSupertypes(): List<String> =
    lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .filter { it.lookupTag.toSymbol(context.session) == null }
        .map { it.lookupTag.classId.asFqNameString() }
        .distinct()

context(context: CheckerContext)
private fun FirRegularClassSymbol.membersWithMissingClasses(): Set<String> {
    val members = sortedSetOf<String>()
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .mapNotNull { it.toRegularClassSymbol(context.session) }
    for (type in listOf(this) + supertypes) {
        type.unsubstitutedScope().processAllCallables { member ->
            val missing = member.erasedClasses().filter { it.lookupTag.toSymbol(context.session) == null }
            if (missing.isNotEmpty()) {
                val classes = missing.map { it.lookupTag.classId.asFqNameString() }
                members += memberWithClasses(member.name.asString(), classes)
            }
        }
    }
    return members
}

context(context: CheckerContext)
private fun FirCallableSymbol<*>.erasedClasses(): List<ConeClassLikeType> =
    signatureTypes().flatMap { it.erasedClasses() }.distinctBy { it.lookupTag }

private fun FirCallableSymbol<*>.signatureTypes(): List<ConeKotlinType> =
    listOfNotNull(resolvedReturnType, resolvedReceiverType) +
        contextParameterSymbols.map { it.resolvedReturnType } +
        (this as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty().map { it.resolvedReturnType }

context(context: CheckerContext)
private fun ConeKotlinType.erasedClasses(): List<ConeClassLikeType> =
    when (val type = lowerBoundIfFlexible()) {
        is ConeClassLikeType -> when {
            type.lookupTag.toSymbol(context.session) != null && type.isNonPrimitiveArray ->
                type.typeArguments.single().type?.erasedClasses().orEmpty()
            else -> listOf(type)
        }
        is ConeTypeParameterType ->
            type.toTypeParameterSymbol(context.session)?.resolvedBounds.orEmpty().flatMap { it.coneType.erasedClasses() }
        is ConeDefinitelyNotNullType -> type.original.erasedClasses()
        else -> emptyList()
    }
