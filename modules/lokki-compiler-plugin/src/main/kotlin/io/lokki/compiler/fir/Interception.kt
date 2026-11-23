package io.lokki.compiler.fir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CONSTRUCTORS
import io.lokki.compiler.CONSTRUCTORS_HOLDER
import io.lokki.compiler.INTERCEPTION_POINTS_KOTLIN
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.REDIRECT
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.processAllDeclaredCallables
import org.jetbrains.kotlin.fir.declarations.utils.compilerPluginMetadata
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.isSubstitutionOrIntersectionOverride
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

internal sealed interface MemberInterception {
    object NotApplicable : MemberInterception

    object Intercepted : MemberInterception

    class NotIntercepted(val reason: String) : MemberInterception
}

context(context: CheckerContext)
internal fun FirRegularClassSymbol.interceptionProblem(): String? {
    if (origin is FirDeclarationOrigin.Source) {
        return DECLARED_HERE.takeIf {
            moduleData == context.session.moduleData && !isMockableInThisCompilation(context.session)
        }
    }
    return markProblem("it or its package")
}

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.objectInterception(receiver: ConeKotlinType? = null): MemberInterception {
    val declaring = getContainingClassSymbol() as? FirRegularClassSymbol
    val receiverObject = receiver?.fullyExpandedType()?.toRegularClassSymbol()
        ?.takeIf { it.isRedirectTarget }
    val owner = receiverObject ?: declaring?.takeIf { it.isRedirectTarget }
        ?: return MemberInterception.NotApplicable
    val inherited = declaring != owner || isSubstitutionOrIntersectionOverride
    val reason = propertyProblem() ?: when {
        this is FirNamedFunctionSymbol && isMemberOfAny -> "it is a member of 'Any'"
        else -> owner.interceptionProblem()?.let { "'${owner.displayName}' $it" }
    }
    return when {
        reason != null -> MemberInterception.NotIntercepted(reason)
        inherited && modality == Modality.FINAL && !isOpenedInBytecode(context.session) ->
            MemberInterception.NotIntercepted("it is a final member inherited by '${owner.displayName}'")
        inherited && owner.origin is FirDeclarationOrigin.Source -> MemberInterception.Intercepted
        inherited && owner.redirectDeclares(name) -> MemberInterception.Intercepted
        inherited -> MemberInterception.NotIntercepted(
            "lokki generated no interception point for it in '${owner.displayName}'"
        )
        owner.origin is FirDeclarationOrigin.Source || mark() == InterceptionMark.CURRENT ->
            MemberInterception.Intercepted
        else -> MemberInterception.NotIntercepted(
            "lokki generated no interception point for it in '${owner.displayName}'"
        )
    }
}

private val FirRegularClassSymbol.isRedirectTarget: Boolean
    get() = classKind == ClassKind.OBJECT || classKind == ClassKind.ENUM_CLASS

context(context: CheckerContext)
internal fun FirRegularClassSymbol.constructorInterceptionProblem(): String? {
    return when {
        classKind != ClassKind.CLASS -> "it is not a class"
        isInner -> "it is an inner class"
        isInlineOrValue -> "it is a value class"
        modality == Modality.ABSTRACT || modality == Modality.SEALED -> "it is abstract, so it has no instances of its own"
        origin is FirDeclarationOrigin.Source -> DECLARED_HERE.takeIf {
            moduleData == context.session.moduleData && !isMockableInThisCompilation(context.session)
        }
        else -> markProblem("it or its package") ?: (
            "it has no constructor interception point: rebuild the module that declares it".takeIf {
                context.session.symbolProvider.getClassLikeSymbolByClassId(classId.createNestedClassId(CONSTRUCTORS)) == null ||
                    context.session.symbolProvider.getClassLikeSymbolByClassId(classId.createNestedClassId(CONSTRUCTORS_HOLDER)) == null
            }
        )
    }
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.redirectDeclares(member: Name): Boolean {
    val redirect = context.session.symbolProvider.getClassLikeSymbolByClassId(classId.createNestedClassId(REDIRECT))
        as? FirRegularClassSymbol ?: return false
    var declared = false
    redirect.processAllDeclaredCallables(context.session) { declared = declared || it.name == member }
    return declared
}

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.staticInterception(): MemberInterception {
    val id = callableId ?: return MemberInterception.NotApplicable
    if (id.classId != null || id.packageName == CallableId.PACKAGE_FQ_NAME_FOR_LOCAL) {
        return MemberInterception.NotApplicable
    }
    val inline = if (this is FirPropertySymbol) getterSymbol?.isInline == true else isInline
    val problem = when {
        inline -> "is inline"
        origin is FirDeclarationOrigin.Source -> DECLARED_HERE.takeIf {
            moduleData == context.session.moduleData && !isMockableInThisCompilation(context.session)
        }
        else -> markProblem("it, its file or its package")
    }
    val reason = propertyProblem() ?: problem?.let { "it $it" }
    return reason?.let(MemberInterception::NotIntercepted) ?: MemberInterception.Intercepted
}

private fun FirCallableSymbol<*>.propertyProblem(): String? =
    when {
        this is FirPropertySymbol && isConst -> "it is a constant, which is inlined at compile time"
        this is FirPropertySymbol && isLateInit -> "it is a lateinit property, whose getter lokki does not intercept"
        else -> null
    }

context(context: CheckerContext)
private fun FirBasedSymbol<*>.markProblem(additions: String): String? =
    when (mark()) {
        InterceptionMark.CURRENT -> null
        InterceptionMark.OTHER_FORMAT ->
            "has interception points of another version of lokki: rebuild the module that declares it"
        InterceptionMark.ABSENT -> missingInterceptionPoints()
            ?: "has no interception points: add $additions to lokki { mockable(...) } of the module that declares it"
    }

context(context: CheckerContext)
private fun missingInterceptionPoints(): String? =
    "has no interception points: lokki generates them on Kotlin $INTERCEPTION_POINTS_KOTLIN or newer"
        .takeUnless { context.session.lokkiEnvironment.interceptionPoints }

@OptIn(SymbolInternals::class)
internal fun FirBasedSymbol<*>.mark(): InterceptionMark =
    InterceptionMark.of(fir.compilerPluginMetadata?.get(BuildConfig.COMPILER_PLUGIN_ID))

private const val DECLARED_HERE =
    "is declared in this compilation and has no interception points: name it in lokki { mockable(...) } " +
        "(in test sources a package pattern does not apply)"
