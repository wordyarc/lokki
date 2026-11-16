package io.lokki.compiler.fir

import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.linked
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copyWithNewDefaults
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotationSafe
import org.jetbrains.kotlin.fir.declarations.isJavaOrEnhancement
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.declarations.utils.isTailRec
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name

internal class MockableStatusTransformer(session: FirSession, private val mockable: MockablePatterns) :
    FirStatusTransformerExtension(session) {
    private val annotated = mockable.names.takeIf { it.isNotEmpty() }?.let { names ->
        DeclarationPredicate.create { annotated(names) }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        annotated?.let { register(it) }
    }

    @OptIn(SymbolInternals::class)
    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        linked {
            when (declaration) {
                is FirRegularClass -> declaration.isMockable()
                is FirPropertyAccessor -> declaration.propertySymbol.fir.isOpenable()
                is FirProperty -> declaration.isOpenable()
                is FirNamedFunction -> declaration.isOpenable()
                else -> false
            }
        }

    @OptIn(SymbolInternals::class)
    override fun transformStatus(status: FirDeclarationStatus, declaration: FirDeclaration): FirDeclarationStatus =
        linked {
            val explicitModality = when (declaration) {
                is FirPropertyAccessor -> declaration.propertySymbol.fir.status.modality
                else -> status.modality
            }
            when (explicitModality) {
                null -> status.copyWithNewDefaults(modality = Modality.OPEN, defaultModality = Modality.OPEN)
                else -> status.copyWithNewDefaults(defaultModality = Modality.OPEN)
            }
        }

    private fun FirRegularClass.isMockable(): Boolean =
        !isJavaOrEnhancement && classKind == ClassKind.CLASS && !isInlineOrValue && !symbol.classId.isLocal &&
            !hasAnnotationSafe(JvmStandardClassIds.JVM_RECORD_ANNOTATION_CLASS_ID, session) &&
            (mockable.matches(symbol.classId) || isAnnotatedMockable())

    private fun FirRegularClass.isAnnotatedMockable(): Boolean =
        annotated != null && session.predicateBasedProvider.matches(annotated, this)

    @OptIn(SymbolInternals::class)
    private fun FirCallableDeclaration.isOpenable(): Boolean {
        val owner = (symbol.getContainingClassSymbol() as? FirRegularClassSymbol)?.fir ?: return false
        return owner.isMockable() && owner.modality != Modality.FINAL && owner.modality != Modality.SEALED &&
            visibility != Visibilities.Private && !isStatic && !breaksWhenOpen()
    }

    @OptIn(SymbolInternals::class)
    private fun FirCallableDeclaration.breaksWhenOpen(): Boolean =
        when (this) {
            is FirNamedFunction -> isInline || isTailRec
            is FirProperty -> isInline || getter?.isInline == true || setter?.isInline == true ||
                setter?.visibility == Visibilities.Private ||
                (annotations + backingField?.annotations.orEmpty() +
                    correspondingValueParameterFromPrimaryConstructor?.fir?.annotations.orEmpty())
                    .any { it.shortName == JVM_FIELD }
            else -> false
        }
}

private val FirAnnotation.shortName: Name?
    get() = when (val type = annotationTypeRef) {
        is FirResolvedTypeRef -> type.coneType.classId?.shortClassName
        is FirUserTypeRef -> type.qualifier.lastOrNull()?.name
        else -> null
    }

private val JVM_FIELD = Name.identifier("JvmField")
