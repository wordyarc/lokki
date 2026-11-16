package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.util.OperatorNameConventions

internal val FirNamedFunctionSymbol.isMemberOfAny: Boolean
    get() = receiverParameterSymbol == null && contextParameterSymbols.isEmpty() && when (name) {
        OperatorNameConventions.EQUALS -> valueParameterSymbols.singleOrNull()?.resolvedReturnType?.isNullableAny == true
        OperatorNameConventions.HASH_CODE, OperatorNameConventions.TO_STRING -> valueParameterSymbols.isEmpty()
        else -> false
    }
