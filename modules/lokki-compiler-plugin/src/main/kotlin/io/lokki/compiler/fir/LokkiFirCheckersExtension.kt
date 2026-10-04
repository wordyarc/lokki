package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class LokkiFirCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session)
