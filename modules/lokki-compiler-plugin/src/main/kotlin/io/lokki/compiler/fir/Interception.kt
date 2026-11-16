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

internal sealed interface MemberInterception {
    object NotObjectMember : MemberInterception

    object Intercepted : MemberInterception

    class NotIntercepted(val reason: String) : MemberInterception
}

context(context: CheckerContext)
internal fun FirRegularClassSymbol.interceptionProblem(): String? {
    if (origin is FirDeclarationOrigin.Source) {
        if (moduleData != context.session.moduleData) return null
        return "is declared in this compilation, and only main compilations with lokki { mockable(...) } " +
            "get interception points"
    }
    return when (mark()) {
        InterceptionMark.CURRENT -> null
        InterceptionMark.OTHER_FORMAT ->
            "has interception points of another version of lokki: rebuild the module that declares it"
        InterceptionMark.ABSENT -> missingInterceptionPoints()
            ?: "has no interception points: add it or its package to lokki { mockable(...) } of the module that " +
                "declares it"
    }
}

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.objectInterception(): MemberInterception {
    val owner = getContainingClassSymbol() as? FirRegularClassSymbol
    if (owner == null || owner.classKind != ClassKind.OBJECT) return MemberInterception.NotObjectMember
    val reason = when {
        this is FirPropertySymbol && isConst -> "it is a constant, which is inlined at compile time"
        this is FirPropertySymbol && isLateInit -> "it is a lateinit property, whose getter lokki does not intercept"
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
    val id = callableId ?: return MemberInterception.NotObjectMember
    if (id.classId != null || id.isLocal) return MemberInterception.NotObjectMember
    val inline = if (this is FirPropertySymbol) getterSymbol?.isInline == true else isInline
    val reason = when {
        this is FirPropertySymbol && isConst -> "it is a constant, which is inlined at compile time"
        this is FirPropertySymbol && isLateInit -> "it is a lateinit property, whose getter lokki does not intercept"
        inline -> "it is inline"
        origin is FirDeclarationOrigin.Source -> if (moduleData == context.session.moduleData) {
            "it is declared in this compilation, and only main compilations with lokki { mockable(...) } get " +
                "interception points"
        } else {
            null
        }
        else -> when (mark()) {
            InterceptionMark.CURRENT -> null
            InterceptionMark.OTHER_FORMAT ->
                "it has interception points of another version of lokki: rebuild the module that declares it"
            InterceptionMark.ABSENT -> missingInterceptionPoints()?.let { "it $it" }
                ?: "it has no interception points: add it, its file or its package to lokki { mockable(...) } of " +
                    "the module that declares it"
        }
    }
    return reason?.let(MemberInterception::NotIntercepted) ?: MemberInterception.Intercepted
}

context(context: CheckerContext)
private fun missingInterceptionPoints(): String? =
    "has no interception points: lokki generates them on Kotlin $INTERCEPTION_POINTS_KOTLIN or newer"
        .takeUnless { context.session.compilerCapabilities.interceptionPoints }

@OptIn(SymbolInternals::class)
private fun FirBasedSymbol<*>.mark(): InterceptionMark =
    InterceptionMark.of(fir.compilerPluginMetadata?.get(BuildConfig.COMPILER_PLUGIN_ID))
