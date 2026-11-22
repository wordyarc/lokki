package io.lokki.compiler.fir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.INTERCEPTION_POINTS_KOTLIN
import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.compilerPluginMetadata
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.CallableId

internal sealed interface MemberInterception {
    object NotApplicable : MemberInterception

    object Intercepted : MemberInterception

    class NotIntercepted(val reason: String) : MemberInterception
}

context(context: CheckerContext)
internal fun FirRegularClassSymbol.interceptionProblem(): String? {
    if (origin is FirDeclarationOrigin.Source) return DECLARED_HERE.takeIf { moduleData == context.session.moduleData }
    return markProblem("it or its package")
}

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.objectInterception(): MemberInterception {
    val owner = getContainingClassSymbol() as? FirRegularClassSymbol
    if (owner == null || owner.classKind != ClassKind.OBJECT) return MemberInterception.NotApplicable
    val reason = propertyProblem() ?: when {
        this is FirNamedFunctionSymbol && isMemberOfAny -> "it is a member of 'Any'"
        else -> owner.interceptionProblem()?.let { "'${owner.displayName}' $it" }
    }
    return when {
        reason != null -> MemberInterception.NotIntercepted(reason)
        owner.origin is FirDeclarationOrigin.Source || mark() == InterceptionMark.CURRENT ->
            MemberInterception.Intercepted
        else -> MemberInterception.NotIntercepted(
            "lokki generated no interception point for it in '${owner.displayName}'"
        )
    }
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
        origin is FirDeclarationOrigin.Source -> DECLARED_HERE.takeIf { moduleData == context.session.moduleData }
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
    "is declared in this compilation, and only main compilations with lokki { mockable(...) } get interception points"
