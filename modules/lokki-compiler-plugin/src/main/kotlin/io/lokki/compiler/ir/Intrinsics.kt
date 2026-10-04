package io.lokki.compiler.ir

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LOKKI_INTRINSIC
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.util.getAnnotation

internal val IrCall.intrinsicKind: IntrinsicKind?
    get() = symbol.owner.intrinsicKind

internal val IrFunction.intrinsicKind: IntrinsicKind?
    get() {
        val annotation = getAnnotation(LOKKI_INTRINSIC.asSingleFqName()) ?: return null
        val kind = annotation.arguments.single() as? IrGetEnumValue ?: return null
        return IntrinsicKind.of(kind.symbol.owner.name.asString())
    }
