package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent

internal class CompilerCapabilities(session: FirSession, val interceptionPoints: Boolean) :
    FirExtensionSessionComponent(session)

internal val FirSession.compilerCapabilities: CompilerCapabilities by FirSession.sessionComponentAccessor()
