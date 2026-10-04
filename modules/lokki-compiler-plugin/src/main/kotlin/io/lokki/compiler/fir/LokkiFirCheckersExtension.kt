package io.lokki.compiler.fir

import io.lokki.compiler.fir.checkers.IntrinsicReferenceChecker
import io.lokki.compiler.fir.checkers.MockChecker
import io.lokki.compiler.fir.checkers.StubBlockChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallableReferenceAccessChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class LokkiFirCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(MockChecker, StubBlockChecker)
        override val callableReferenceAccessCheckers: Set<FirCallableReferenceAccessChecker> =
            setOf(IntrinsicReferenceChecker)
    }
}
