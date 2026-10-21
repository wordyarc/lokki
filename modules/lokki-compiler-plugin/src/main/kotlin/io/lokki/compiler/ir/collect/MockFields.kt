package io.lokki.compiler.ir.collect

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructedClass

internal class MockField(val property: IrProperty, val annotation: IrConstructorCall, val kind: IntrinsicKind) {
    val annotationName: String
        get() = annotation.symbol.owner.constructedClass.name.asString()

    val name: String
        get() = ((annotation.argument("name") as? IrConst)?.value as? String)?.takeIf { it.isNotBlank() }
            ?: property.name.asString()

    val additionalInterface: IrType?
        get() = property.annotations.find { it.annotationKind == IntrinsicKind.ADDITIONAL_INTERFACE }
            ?.let { (it.argument("type") as? IrClassReference)?.classType }

    val constructorParameter: IrValueParameter?
        get() = (property.backingField?.initializer?.expression as? IrGetValue)
            ?.takeIf { it.origin == IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER }
            ?.symbol?.owner as? IrValueParameter

    fun isConstructedSpy(file: IrFile): Boolean =
        kind == IntrinsicKind.SPY && property.backingField?.initializer != null &&
            mockedTypes(file)?.form is MockForm.Construction

    fun unsupportedReason(file: IrFile): String? =
        when {
            constructorParameter != null -> "'@$annotationName' on a constructor parameter is not supported yet"
            property.parent !is IrClass -> "'@$annotationName' initializes only properties of classes"
            property.backingField == null -> "'@$annotationName' needs a property with a backing field"
            else -> notMockableReason(file)
                ?: "'@$annotationName' needs a 'lateinit var' or 'var' property"
                    .takeIf { !property.isVar && !isConstructedSpy(file) }
        }

    fun isInitialized(file: IrFile): Boolean = unsupportedReason(file) == null && !isConstructedSpy(file)
}

internal val IrProperty.mockField: MockField?
    get() = annotations.firstNotNullOfOrNull { annotation ->
        annotation.annotationKind?.takeIf { it in FIELD_KINDS }?.let { MockField(this, annotation, it) }
    }

internal val IrAnnotationContainer.mockAnnotationName: String?
    get() = annotations.find { it.annotationKind in FIELD_KINDS }?.symbol?.owner?.constructedClass?.name?.asString()

internal val IrProperty.valueType: IrType
    get() = backingField?.type ?: getter!!.returnType

internal val IrClass.mockFields: List<MockField>
    get() = declarations.mapNotNull { (it as? IrProperty)?.mockField }

private val IrConstructorCall.annotationKind: IntrinsicKind?
    get() = symbol.owner.constructedClass.intrinsicKind

private val FIELD_KINDS = setOf(IntrinsicKind.MOCK, IntrinsicKind.RELAXED_MOCK, IntrinsicKind.SPY)
