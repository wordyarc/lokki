package io.lokki.compiler.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

internal val FirRegularClassSymbol.isFinalType: Boolean
    get() = when (classKind) {
        ClassKind.INTERFACE -> false
        ClassKind.CLASS -> modality == Modality.FINAL
        else -> true
    }

internal val FirRegularClassSymbol.displayName: String
    get() = if (isLocal) name.asString() else classId.asFqNameString()
