package io.lokki.compiler.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.isJavaOrEnhancement
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

internal val FirRegularClassSymbol.isFinalType: Boolean
    get() = when (classKind) {
        ClassKind.INTERFACE -> false
        ClassKind.CLASS -> modality == Modality.FINAL
        else -> true
    }

context(context: CheckerContext)
internal val FirRegularClassSymbol.isFromFriendKotlinModule: Boolean
    get() = !isJavaOrEnhancement && moduleData != context.session.moduleData &&
        context.session.moduleData.canSeeInternalsOf(moduleData)

internal val FirRegularClassSymbol.displayName: String
    get() = if (isLocal) name.asString() else classId.asFqNameString()
