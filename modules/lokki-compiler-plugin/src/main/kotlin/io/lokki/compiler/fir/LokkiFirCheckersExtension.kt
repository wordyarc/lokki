package io.lokki.compiler.fir

import io.lokki.compiler.fir.checkers.IntrinsicReferenceChecker
import io.lokki.compiler.fir.checkers.LokkiCallChecker
import io.lokki.compiler.fir.checkers.MockParameterChecker
import io.lokki.compiler.fir.checkers.MockPropertyChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallableReferenceAccessChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

internal class LokkiFirCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(MockPropertyChecker)
        override val valueParameterCheckers: Set<FirValueParameterChecker> = setOf(MockParameterChecker)
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(LokkiCallChecker)
        override val callableReferenceAccessCheckers: Set<FirCallableReferenceAccessChecker> =
            setOf(IntrinsicReferenceChecker)
    }
}
