package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol

internal fun FirCall.argument(parameter: FirValueParameterSymbol): FirExpression? =
    resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key

internal fun FirCall.argument(name: String): FirExpression? =
    resolvedArgumentMapping?.entries?.find { it.value.name.asString() == name }?.key

internal fun FirCall.blockArgument(function: FirCallableSymbol<*>): FirExpression? =
    (function as? FirFunctionSymbol<*>)?.valueParameterSymbols?.lastOrNull()?.let { argument(it) }?.unwrapArgument()
