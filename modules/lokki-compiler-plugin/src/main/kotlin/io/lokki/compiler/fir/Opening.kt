package io.lokki.compiler.fir

import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.isTailRec
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides

internal fun FirRegularClassSymbol.isOpenedInBytecode(session: FirSession): Boolean =
    classKind == ClassKind.CLASS && (mark() == InterceptionMark.CURRENT || isUnmarkedSource(session))

internal fun FirRegularClassSymbol.effectiveModality(session: FirSession): Modality? =
    if (modality == Modality.FINAL && isOpenedInBytecode(session)) Modality.OPEN else modality

internal fun FirCallableSymbol<*>.isOpenedInBytecode(session: FirSession): Boolean {
    val declaration = unwrapFakeOverrides().let { (it as? FirPropertyAccessorSymbol)?.propertySymbol ?: it }
    if (declaration.mark() == InterceptionMark.CURRENT) return true
    val owner = declaration.containingClassLookupTag()?.toRegularClassSymbol(session) ?: return false
    val inline = if (declaration is FirPropertySymbol) declaration.getterSymbol?.isInline == true else declaration.isInline
    return owner.isUnmarkedSource(session) && !inline && !declaration.isTailRec
}

private fun FirRegularClassSymbol.isUnmarkedSource(session: FirSession): Boolean =
    origin is FirDeclarationOrigin.Source && moduleData != session.moduleData && !isInlineOrValue
