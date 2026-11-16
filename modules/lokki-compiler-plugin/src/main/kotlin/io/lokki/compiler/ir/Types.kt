package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance

internal fun IrType.renderReadable(parameterName: (IrTypeParameter) -> String = { it.name.asString() }): String =
    when (val classifier = classifierOrNull) {
        is IrTypeParameterSymbol -> parameterName(classifier.owner)
        is IrClassSymbol -> buildString {
            generateSequence(classifier.owner) { it.parent as? IrClass }.toList().asReversed()
                .joinTo(this, ".") { it.name.asString() }
            val arguments = (this@renderReadable as? IrSimpleType)?.arguments.orEmpty()
            if (arguments.isNotEmpty()) {
                arguments.joinTo(this, ", ", "<", ">") { argument ->
                    when (argument) {
                        is IrStarProjection -> "*"
                        is IrTypeProjection -> argument.variance.prefix + argument.type.renderReadable(parameterName)
                    }
                }
            }
            if (isMarkedNullable() && !hasAnnotation(StandardClassIds.Annotations.FlexibleNullability)) append('?')
        }
        else -> render()
    }

internal val Variance.prefix: String
    get() = if (this == Variance.INVARIANT) "" else "$label "
