package io.lokki.compiler.fir

import org.jetbrains.kotlin.fir.types.ConeClassifierLookupTag
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType

internal val ConeLookupTagBasedType.classifier: ConeClassifierLookupTag
    get() = lookupTag
