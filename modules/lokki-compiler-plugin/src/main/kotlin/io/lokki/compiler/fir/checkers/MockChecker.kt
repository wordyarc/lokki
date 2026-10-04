package io.lokki.compiler.fir.checkers

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isFinalType
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

internal object MockChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (function.intrinsicKind(context.session) != IntrinsicKind.MOCK) return
        val argument = expression.typeArguments.singleOrNull() ?: return
        val source = argument.source?.takeIf { it.kind is KtRealSourceElementKind } ?: expression.source
        val type = argument.toConeTypeProjection().type?.lowerBoundIfFlexible()?.fullyExpandedType() ?: return
        if (type is ConeTypeParameterType) {
            reporter.reportOn(
                source,
                LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
                function.name.asString(),
                type.lookupTag.name.asString(),
            )
            return
        }
        val mocked = type.takeUnless { it.isNothing }?.toRegularClassSymbol(context.session) ?: return
        val reason = mocked.notMockableReason ?: return
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, reason)
    }
}

private val FirRegularClassSymbol.notMockableReason: String?
    get() = when {
        classKind == ClassKind.OBJECT -> "it is an object"
        classKind == ClassKind.ENUM_CLASS || classKind == ClassKind.ENUM_ENTRY -> "it is an enum class"
        classKind == ClassKind.ANNOTATION_CLASS -> "it is an annotation class"
        modality == Modality.SEALED -> "it is sealed"
        isFinalType -> "it is a final class. Extract an interface or make the class and its members open"
        else -> null
    }
