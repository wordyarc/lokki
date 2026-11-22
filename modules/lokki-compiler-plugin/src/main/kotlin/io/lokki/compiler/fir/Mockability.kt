package io.lokki.compiler.fir

import io.lokki.compiler.declarationKindProblem
import io.lokki.compiler.memberWithClasses
import io.lokki.compiler.missingMemberClassesProblem
import io.lokki.compiler.missingSupertypesProblem
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.toTypeParameterSymbol
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
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
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

context(context: CheckerContext)
internal fun FirRegularClassSymbol.mockabilityProblem(): String? =
    declarationKindProblem(
        classKind,
        modality,
        suggestsMockable = isFromFriendKotlinModule(context.session) && !isInlineOrValue,
    ) ?: context.session.lokkiEnvironment.missingClassProblems.getValue(this, context.sessionHolder)

context(context: CheckerContext)
internal fun FirRegularClassSymbol.restriction(): String? =
    context.session.lokkiEnvironment.restrictions.getValue(this)

context(context: CheckerContext)
internal fun FirRegularClassSymbol.finalMembers(): List<String> =
    context.session.lokkiEnvironment.finalMembers.getValue(this, context.sessionHolder)

context(holder: SessionAndScopeSessionHolder)
internal fun FirRegularClassSymbol.findMissingClassesProblem(): String? =
    missingSupertypes().takeIf { it.isNotEmpty() }?.let(::missingSupertypesProblem)
        ?: membersWithMissingClasses().takeIf { it.isNotEmpty() }?.let(::missingMemberClassesProblem)

context(holder: SessionAndScopeSessionHolder)
internal fun FirRegularClassSymbol.findFinalMembers(): List<String> {
    if (classKind != ClassKind.CLASS) return emptyList()
    val members = sortedSetOf<String>()
    memberScope().processAllCallables { member ->
        val owner = member.unwrapFakeOverrides().containingClassLookupTag()?.toRegularClassSymbol(holder.session)
        if (member.isFinal && member.visibility != Visibilities.Private &&
            owner != null && holder.session.moduleData.canSeeInternalsOf(owner.moduleData)
        ) {
            members += member.name.asString()
        }
    }
    return members.toList()
}

internal fun FirRegularClassSymbol.findRestriction(session: FirSession): String? {
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, session)
        .mapNotNull { it.toRegularClassSymbol(session) }
    for (type in listOf(this) + supertypes) {
        val annotation = type.resolvedAnnotationsWithArguments
            .find { it.toAnnotationClassId(session)?.shortClassName == DO_NOT_MOCK }
        if (annotation != null) {
            val advice = annotation.getStringArgument(VALUE)?.trimEnd('.')
            return "'${type.displayName}' is annotated with '$DO_NOT_MOCK'" + advice?.let { ": $it" }.orEmpty()
        }
        if (type.classId in RESTRICTED_TYPES) return "use a real '${type.displayName}' instead"
    }
    return null
}

context(holder: SessionAndScopeSessionHolder)
private fun FirRegularClassSymbol.memberScope(): FirTypeScope =
    unsubstitutedScope(
        holder.session,
        holder.scopeSession,
        withForcedTypeCalculator = true,
        memberRequiredPhase = FirResolvePhase.STATUS,
    )

context(holder: SessionAndScopeSessionHolder)
private fun FirRegularClassSymbol.missingSupertypes(): List<String> =
    lookupSuperTypes(this, lookupInterfaces = true, deep = true, holder.session)
        .filter { it.lookupTag.toSymbol(holder.session) == null }
        .map { it.lookupTag.classId.asFqNameString() }
        .distinct()

context(holder: SessionAndScopeSessionHolder)
private fun FirRegularClassSymbol.membersWithMissingClasses(): Set<String> {
    val members = sortedSetOf<String>()
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, holder.session)
        .mapNotNull { it.toRegularClassSymbol(holder.session) }
    for (type in listOf(this) + supertypes) {
        type.memberScope().processAllCallables { member ->
            val missing = member.erasedClasses().filter { it.lookupTag.toSymbol(holder.session) == null }
            if (missing.isNotEmpty()) {
                val classes = missing.map { it.lookupTag.classId.asFqNameString() }
                members += memberWithClasses(member.name.asString(), classes)
            }
        }
    }
    return members
}

context(holder: SessionAndScopeSessionHolder)
private fun FirCallableSymbol<*>.erasedClasses(): List<ConeClassLikeType> =
    signatureTypes().flatMap { it.erasedClasses() }.distinctBy { it.lookupTag }

private fun FirCallableSymbol<*>.signatureTypes(): List<ConeKotlinType> =
    listOfNotNull(resolvedReturnType, resolvedReceiverType) +
        contextParameterSymbols.map { it.resolvedReturnType } +
        (this as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty().map { it.resolvedReturnType }

context(holder: SessionAndScopeSessionHolder)
private fun ConeKotlinType.erasedClasses(): List<ConeClassLikeType> =
    when (val type = lowerBoundIfFlexible()) {
        is ConeClassLikeType -> when {
            type.lookupTag.toSymbol(holder.session) != null && type.isNonPrimitiveArray ->
                type.typeArguments.single().type?.erasedClasses().orEmpty()
            else -> listOf(type)
        }
        is ConeTypeParameterType ->
            type.toTypeParameterSymbol(holder.session)?.resolvedBounds.orEmpty().flatMap { it.coneType.erasedClasses() }
        is ConeDefinitelyNotNullType -> type.original.erasedClasses()
        else -> emptyList()
    }

private val DO_NOT_MOCK = Name.identifier("DoNotMock")
private val VALUE = Name.identifier("value")
private val RESTRICTED_TYPES = setOf(
    StandardClassIds.Collection,
    ClassId.fromString("java/util/HashMap"),
    ClassId.fromString("java/io/File"),
    ClassId.fromString("java/nio/file/Path"),
)
