package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.name.FqName

internal enum class IntrinsicKind {
    MOCK,
    STUB_BLOCK,
    MATCHER,
}

internal val IrCall.intrinsicKind: IntrinsicKind?
    get() {
        val kind = symbol.owner.getAnnotation(LOKKI_INTRINSIC)?.arguments?.single() as? IrGetEnumValue ?: return null
        val name = kind.symbol.owner.name.asString()
        return IntrinsicKind.entries.find { it.name == name }
    }

private val LOKKI_INTRINSIC = FqName("io.lokki.internal.LokkiIntrinsic")
