package io.lokki.compiler.fir

import io.lokki.compiler.FINALIZE
import io.lokki.compiler.InterceptionMark
import io.lokki.compiler.MockablePatterns
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.isTailRec
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

internal fun FirRegularClassSymbol.isOpenedInBytecode(session: FirSession): Boolean =
    classKind == ClassKind.CLASS &&
        (mark() == InterceptionMark.CURRENT || isUnmarkedSource(session) || isMockableInThisCompilation(session))

internal fun FirRegularClassSymbol.effectiveModality(session: FirSession): Modality? =
    if (modality == Modality.FINAL && isOpenedInBytecode(session)) Modality.OPEN else modality

internal fun FirCallableSymbol<*>.isOpenedInBytecode(session: FirSession): Boolean {
    val declaration = unwrapFakeOverrides().let { (it as? FirPropertyAccessorSymbol)?.propertySymbol ?: it }
    if (declaration.mark() == InterceptionMark.CURRENT) return true
    val owner = declaration.containingClassLookupTag()?.toRegularClassSymbol(session) ?: return false
    val inline = if (declaration is FirPropertySymbol) declaration.getterSymbol?.isInline == true else declaration.isInline
    val opened = owner.isUnmarkedSource(session) || owner.isMockableInThisCompilation(session)
    return opened && !inline && !declaration.isTailRec
}

internal fun FirCallableSymbol<*>.isClassFinalizer(session: FirSession): Boolean {
    if (this !is FirNamedFunctionSymbol || name.asString() != FINALIZE) return false
    if (valueParameterSymbols.isNotEmpty() || receiverParameterSymbol != null) return false
    if (contextParameterSymbols.isNotEmpty() || !resolvedReturnType.isUnit) return false
    val declared = unwrapFakeOverrides()
    val owner = declared.containingClassLookupTag()?.toRegularClassSymbol(session) ?: return false
    return declared.modality != Modality.ABSTRACT && owner.classKind != ClassKind.INTERFACE
}

internal fun FirRegularClassSymbol.isMockableInThisCompilation(session: FirSession): Boolean {
    val patterns = session.lokkiEnvironment.mockable ?: return false
    if (origin !is FirDeclarationOrigin.Source || moduleData != session.moduleData || isInlineOrValue) return false
    val outer = classId.outerClassId?.takeIf { isCompanion }
        ?.let { session.symbolProvider.getClassLikeSymbolByClassId(it) as? FirRegularClassSymbol }
    return listOfNotNull(this, outer).any { it.matches(patterns, session) }
}

internal fun FirCallableSymbol<*>.isMockableInThisCompilation(session: FirSession): Boolean {
    val patterns = session.lokkiEnvironment.mockable ?: return false
    val id = callableId ?: return false
    if (origin !is FirDeclarationOrigin.Source || moduleData != session.moduleData) return false
    if (patterns.matches(id) || hasAnnotationFrom(patterns, session)) return true
    val file = session.firProvider.getFirCallableContainerFile(this) ?: return false
    return file.annotations.any { annotation ->
        annotation.toAnnotationClassId(session)?.asSingleFqName() in patterns.names
    }
}

private fun FirRegularClassSymbol.matches(patterns: MockablePatterns, session: FirSession): Boolean =
    patterns.matches(classId) || hasAnnotationFrom(patterns, session)

private fun FirBasedSymbol<*>.hasAnnotationFrom(patterns: MockablePatterns, session: FirSession): Boolean =
    resolvedAnnotationsWithClassIds.any { it.toAnnotationClassId(session)?.asSingleFqName() in patterns.names }

private fun FirRegularClassSymbol.isUnmarkedSource(session: FirSession): Boolean =
    origin is FirDeclarationOrigin.Source && moduleData != session.moduleData && !isInlineOrValue
