package io.lokki.compiler.fir

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LOKKI_INTRINSIC
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.Name

internal fun FirCallableSymbol<*>.intrinsicKind(session: FirSession): IntrinsicKind? {
    val kind = resolvedAnnotationsWithArguments.getAnnotationByClassId(LOKKI_INTRINSIC, session)
        ?.findArgumentByName(KIND)
        ?.extractEnumValueArgumentInfo()
        ?: return null
    return IntrinsicKind.of(kind.enumEntryName.asString())
}

private val KIND = Name.identifier("kind")
