package io.lokki.compiler.ir

import io.lokki.compiler.CoreClass
import io.lokki.compiler.IntrinsicKind
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.util.getAnnotation

internal val IrCall.intrinsicKind: IntrinsicKind?
    get() = symbol.owner.intrinsicKind

internal val IrAnnotationContainer.intrinsicKind: IntrinsicKind?
    get() {
        val annotation = getAnnotation(CoreClass.LOKKI_INTRINSIC.id.asSingleFqName()) ?: return null
        val kind = annotation.arguments.single() as? IrGetEnumValue ?: return null
        return IntrinsicKind.of(kind.symbol.owner.name.asString())
    }
