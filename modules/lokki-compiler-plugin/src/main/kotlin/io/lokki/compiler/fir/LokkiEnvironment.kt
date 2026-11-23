package io.lokki.compiler.fir

import io.lokki.compiler.ABI_VERSION
import io.lokki.compiler.CoreClass
import io.lokki.compiler.MockablePatterns
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.caches.FirCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

internal class LokkiEnvironment(
    session: FirSession,
    val interceptionPoints: Boolean,
    val mockable: MockablePatterns?,
    private val restricted: MockablePatterns?,
) : FirExtensionSessionComponent(session) {
    val runtimeAbi: Int by session.firCachesFactory.createLazyValue {
        val abi = session.symbolProvider.getClassLikeSymbolByClassId(CoreClass.LOKKI_ABI.id)
            as? FirRegularClassSymbol
        val version = abi?.declaredProperties(session)?.singleOrNull { it.name.asString() == ABI_VERSION }
        ((version?.resolvedInitializer as? FirLiteralExpression)?.value as? Number)?.toInt() ?: 0
    }

    val missingClassProblems: FirCache<FirRegularClassSymbol, String?, SessionAndScopeSessionHolder> =
        session.firCachesFactory.createCache { type, holder -> with(holder) { type.findMissingClassesProblem() } }

    val restrictions: FirCache<FirRegularClassSymbol, String?, Nothing?> =
        session.firCachesFactory.createCache { mocked, _ -> mocked.findRestriction(session, restricted) }

    val finalMembers: FirCache<FirRegularClassSymbol, List<String>, SessionAndScopeSessionHolder> =
        session.firCachesFactory.createCache { mocked, holder -> with(holder) { mocked.findFinalMembers() } }
}

internal val FirSession.lokkiEnvironment: LokkiEnvironment by FirSession.sessionComponentAccessor()
