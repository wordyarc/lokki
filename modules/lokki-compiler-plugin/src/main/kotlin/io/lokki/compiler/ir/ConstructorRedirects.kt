package io.lokki.compiler.ir

import io.lokki.compiler.CONSTRUCTORS_PROPERTY_SUFFIX
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.name.Name

internal class ConstructorRedirect(val target: IrClass, val redirect: IrClass, val property: IrProperty) {
    val key: String
        get() = target.classIdOrFail.asString()

    val constructors: List<IrConstructor>
        get() = target.constructors.filter { create(it) != null }.toList()

    fun create(constructor: IrConstructor): IrSimpleFunction? =
        redirect.functions.singleOrNull { it.name == constructor.createName }

    fun construct(constructor: IrConstructor): IrSimpleFunction? =
        redirect.functions.singleOrNull { it.name == constructor.constructName }
}

internal val IrClass.constructorsPropertyName: Name
    get() = Name.identifier("$flatName$CONSTRUCTORS_PROPERTY_SUFFIX")

internal val IrConstructor.createName: Name
    get() = Name.identifier("create\$$signatureHash")

internal val IrConstructor.constructName: Name
    get() = Name.identifier("construct\$$signatureHash")

internal val IrConstructor.declaredParameters
    get() = parameters.filter { it.kind == IrParameterKind.Regular }

private val IrClass.flatName: String
    get() = classIdOrFail.relativeClassName.asString().replace('.', '$')

@OptIn(ExperimentalStdlibApi::class)
private val IrConstructor.signatureHash: String
    get() {
        val typeParameters = constructedClass.typeParameters.map { it.symbol }
        return declaredParameters.joinToString(",") { it.type.typeKey(typeParameters) }.hashCode().toHexString()
    }
