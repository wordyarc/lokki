package io.lokki.compiler.fir

import io.lokki.compiler.ABI_VERSION
import io.lokki.compiler.LOKKI_ABI
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

internal class LokkiEnvironment(session: FirSession, val interceptionPoints: Boolean) :
    FirExtensionSessionComponent(session) {
    val runtimeAbi: Int by session.firCachesFactory.createLazyValue {
        val abi = session.symbolProvider.getClassLikeSymbolByClassId(LOKKI_ABI) as? FirRegularClassSymbol
        val version = abi?.declaredProperties(session)?.singleOrNull { it.name.asString() == ABI_VERSION }
        ((version?.resolvedInitializer as? FirLiteralExpression)?.value as? Number)?.toInt() ?: 0
    }
}

internal val FirSession.lokkiEnvironment: LokkiEnvironment by FirSession.sessionComponentAccessor()
