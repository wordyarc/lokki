package io.lokki.compiler.fir

import io.lokki.compiler.FIELD_KINDS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LOKKI_INTRINSIC
import io.lokki.compiler.ParameterNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.name.Name

internal fun FirBasedSymbol<*>.intrinsicKind(session: FirSession): IntrinsicKind? {
    val kind = resolvedAnnotationsWithArguments.getAnnotationByClassId(LOKKI_INTRINSIC, session)
        ?.findArgumentByName(KIND)
        ?.extractEnumValueArgumentInfo()
        ?: return null
    return IntrinsicKind.of(kind.enumEntryName.asString())
}

private val KIND = Name.identifier(ParameterNames.KIND)

internal fun FirAnnotation.fieldKind(session: FirSession): IntrinsicKind? =
    toAnnotationClassLikeSymbol(session)?.intrinsicKind(session)?.takeIf { it in FIELD_KINDS }
