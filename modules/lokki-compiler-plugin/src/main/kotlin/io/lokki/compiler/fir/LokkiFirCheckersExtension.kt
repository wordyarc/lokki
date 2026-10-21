package io.lokki.compiler.fir

import io.lokki.compiler.fir.checkers.InjectMocksChecker
import io.lokki.compiler.fir.checkers.IntrinsicReferenceChecker
import io.lokki.compiler.fir.checkers.MockChecker
import io.lokki.compiler.fir.checkers.MockObjectChecker
import io.lokki.compiler.fir.checkers.MockParameterChecker
import io.lokki.compiler.fir.checkers.MockPropertyChecker
import io.lokki.compiler.fir.checkers.StubAnswerChecker
import io.lokki.compiler.fir.checkers.StubBlockChecker
import io.lokki.compiler.fir.checkers.VerificationChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallableReferenceAccessChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class LokkiFirCheckersExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val propertyCheckers: Set<FirPropertyChecker> = setOf(MockPropertyChecker)
        override val valueParameterCheckers: Set<FirValueParameterChecker> = setOf(MockParameterChecker)
        override val regularClassCheckers: Set<FirRegularClassChecker> = setOf(InjectMocksChecker)
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> =
            setOf(MockChecker, MockObjectChecker, StubBlockChecker, StubAnswerChecker, VerificationChecker)
        override val callableReferenceAccessCheckers: Set<FirCallableReferenceAccessChecker> =
            setOf(IntrinsicReferenceChecker)
    }
}
