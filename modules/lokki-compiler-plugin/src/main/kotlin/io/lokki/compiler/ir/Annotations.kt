package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.primaryConstructor

internal val IrAnnotation.annotationClass: IrClass?
    get() = type.classOrNull?.owner

internal fun IrAnnotation.argument(name: String): IrExpression? {
    val parameter = annotationClass?.primaryConstructor?.parameters?.find { it.name.asString() == name } ?: return null
    return arguments[parameter]
}
