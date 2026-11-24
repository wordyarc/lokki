package io.lokki.compiler.ir.collect

import io.lokki.compiler.EXTERNAL_KINDS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.annotationClass
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class ExternalMockField(
    val property: IrProperty,
    val annotation: IrAnnotation,
    val kind: IntrinsicKind,
    val index: Int,
    val site: MockSite,
)

internal class ExternalMocks(val owners: Map<IrClass, List<ExternalMockField>>, val misplaced: List<IrAnnotation>) {
    val fields: Map<IrProperty, ExternalMockField> = owners.values.flatten().associateBy { it.property }
}

internal fun IrFile.collectExternalMocks(mockability: Mockability): ExternalMocks {
    val collector = ExternalMockCollector(mockability)
    acceptChildrenVoid(collector)
    return ExternalMocks(collector.owners, collector.misplaced)
}

private class ExternalMockCollector(private val mockability: Mockability) : IrVisitorVoid() {
    val owners = LinkedHashMap<IrClass, List<ExternalMockField>>()
    val misplaced = ArrayList<IrAnnotation>()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val annotated = declaration.declarations.filterIsInstance<IrProperty>().mapNotNull { property ->
            property.externalAnnotation?.let { property to it }
        }
        if (annotated.isNotEmpty()) {
            owners[declaration] = annotated.mapIndexed { index, (property, annotation) ->
                val kind = annotation.annotationClass!!.intrinsicKind!!
                ExternalMockField(property, annotation, kind, index, site(property, annotation, kind))
            }
        }
        super.visitClass(declaration)
    }

    override fun visitProperty(declaration: IrProperty) {
        if (declaration.parent !is IrClass) declaration.externalAnnotation?.let(misplaced::add)
        super.visitProperty(declaration)
    }

    private fun site(property: IrProperty, annotation: IrAnnotation, kind: IntrinsicKind): MockSite {
        val type = property.valueType.makeNotNull()
        if (kind == IntrinsicKind.EXTERNAL_SPY) {
            return when (val site = mockability.site(MockRequest(true, type, annotation, emptyList(), ""))) {
                is MockSite.Mocked -> MockSite.Mocked(site.types)
                else -> site
            }
        }
        val interfaces = annotation.extraInterfaces
        val site = mockability.site(MockRequest(false, type, null, interfaces, "'extraInterfaces'"))
        return if (site is MockSite.Mocked && property.chainsMocks) site.chained() else site
    }
}

private val IrProperty.externalAnnotation: IrAnnotation?
    get() = backingField?.annotations?.find { it.annotationClass?.intrinsicKind in EXTERNAL_KINDS }

private val IrAnnotation.extraInterfaces: List<AddedInterface>?
    get() {
        val literals = (argument(ParameterNames.EXTRA_INTERFACES) as? IrVararg)?.elements ?: return emptyList()
        return literals.map { literal -> (literal as? IrClassReference)?.let { AddedInterface(it.classType, it) } ?: return null }
    }
