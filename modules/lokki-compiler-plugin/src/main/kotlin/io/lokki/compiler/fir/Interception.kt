package io.lokki.compiler.fir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.compilerPluginMetadata
import org.jetbrains.kotlin.fir.declarations.utils.isConst
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.declarations.utils.isMethodOfAny
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
        InterceptionMark.ABSENT ->
            "has no interception points: add it or its package to lokki { mockable(...) } of the module that " +
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
        this is FirNamedFunctionSymbol && isMethodOfAny -> "it is a member of 'Any'"
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

@OptIn(SymbolInternals::class)
private fun FirBasedSymbol<*>.mark(): InterceptionMark =
    InterceptionMark.of(fir.compilerPluginMetadata?.get(BuildConfig.COMPILER_PLUGIN_ID))
