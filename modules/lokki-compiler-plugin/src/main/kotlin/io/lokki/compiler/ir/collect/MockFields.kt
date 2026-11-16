package io.lokki.compiler.ir.collect

import io.lokki.compiler.FIELD_KINDS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.renderReadable
import org.jetbrains.kotlin.ir.IrElement
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
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockField(
    val property: IrProperty,
    val annotation: IrConstructorCall,
    val kind: IntrinsicKind,
    val inject: IrConstructorCall?,
    val problem: MockProblem?,
    val site: MockSite.Mocked?,
    val injection: Injection?,
) {
    val name: String
        get() = ((annotation.argument(ParameterNames.NAME) as? IrConst)?.value as? String)?.takeIf { it.isNotBlank() }
            ?: property.name.asString()

    val isConstructedSpy: Boolean = property.constructsSpy(kind, site)

    val isInitialized: Boolean
        get() = problem == null && (inject != null || !isConstructedSpy)
}

internal class MockFields(val fields: Map<IrProperty, MockField>, val owners: Map<IrClass, ClassMockFields>)

internal class ClassMockFields(val fields: List<MockField>, val injected: List<MockField>)

internal fun IrFile.collectMockFields(mockability: Mockability, typeSystem: IrTypeSystemContext): MockFields {
    val collector = MockFieldCollector(mockability, InjectionPlanner(this, typeSystem))
    acceptChildrenVoid(collector)
    return MockFields(collector.fields, collector.owners)
}

private class MockFieldCollector(
    private val mockability: Mockability,
    private val planner: InjectionPlanner,
) : IrVisitorVoid() {
    val fields = LinkedHashMap<IrProperty, MockField>()
    val owners = LinkedHashMap<IrClass, ClassMockFields>()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        val annotated = declaration.declarations.mapNotNull { (it as? IrProperty)?.annotated }
        if (annotated.isNotEmpty()) {
            val owner = resolve(declaration, annotated)
            owner.fields.associateByTo(fields) { it.property }
            owners[declaration] = owner
        }
        super.visitClass(declaration)
    }

    override fun visitProperty(declaration: IrProperty) {
        if (declaration.parent !is IrClass) {
            declaration.annotated?.let { fields[declaration] = it.field(it.declarationProblem(), null, null) }
        }
        super.visitProperty(declaration)
    }

    private fun resolve(owner: IrClass, annotated: List<Annotated>): ClassMockFields {
        val problems = annotated.associateWith { it.declarationProblem() }
        val targets = annotated.filter { it.inject != null && problems[it] == null }
        val plan = planner.plan(owner, targets.map { InjectionRequest(it.property, it.inject!!) })
        val fields = annotated.map { field ->
            val result = plan.results[field.property]
            val injection = result as? Injection
            val failed = (result as? InjectionProblem)?.let { FailedInjection(it, field.typeName) }
            val site = if (field.isMock && problems[field] == null && failed == null) {
                mockability.site(field.request(injection))
            } else {
                null
            }
            val problem = problems[field] ?: failed ?: (site as? MockSite.Rejected)?.problem
            val mocked = site as? MockSite.Mocked
            field.field(problem ?: field.immutableProblem(mocked), mocked, injection)
        }
        val byProperty = fields.associateBy { it.property }
        val injected = plan.order.map(byProperty::getValue).filter { it.problem == null }
        return ClassMockFields(fields, injected)
    }

    private fun Annotated.declarationProblem(): MockProblem? =
        when {
            property.constructorParameter != null ->
                Unsupported("'@$annotationName' on a constructor parameter is not supported yet")
            property.parent !is IrClass -> Unsupported("'@$annotationName' initializes only properties of classes")
            property.backingField == null -> Unsupported("'@$annotationName' needs a property with a backing field")
            inject == null -> null
            kind != IntrinsicKind.INJECT_MOCKS && kind != IntrinsicKind.SPY ->
                Unsupported("A property cannot have both '@InjectMocks' and '@$annotationName'")
            !property.isVar -> Unsupported("'@InjectMocks' needs a 'lateinit var' or 'var' property")
            kind == IntrinsicKind.SPY -> mockability.problemOf(property.valueType.makeNotNull())
            else -> null
        }

    private fun Annotated.immutableProblem(site: MockSite.Mocked?): MockProblem? {
        if (inject != null || property.isVar || property.constructsSpy(kind, site)) return null
        return Unsupported("'@$annotationName' needs a 'lateinit var' or 'var' property")
    }

    private fun Annotated.request(injection: Injection?): MockRequest {
        val instance = property.backingField?.initializer?.expression
        return MockRequest(
            kind == IntrinsicKind.SPY,
            property.valueType.makeNotNull(),
            instance,
            listOfNotNull(additionalInterface),
            "'@AdditionalInterface'",
            injection?.takeIf { instance == null }?.construction,
        )
    }
}

private class Annotated(
    val property: IrProperty,
    val annotation: IrConstructorCall,
    val kind: IntrinsicKind,
    val inject: IrConstructorCall?,
) {
    val annotationName: String
        get() = annotation.symbol.owner.constructedClass.name.asString()

    val additionalInterface: AddedInterface?
        get() = property.annotations.find { it.annotationKind == IntrinsicKind.ADDITIONAL_INTERFACE }
            ?.let { it.argument(ParameterNames.INTERFACE_TYPE) as? IrClassReference }
            ?.let { AddedInterface(it.classType, it) }

    val isMock: Boolean
        get() = inject == null || kind == IntrinsicKind.SPY

    val typeName: String
        get() {
            val type = property.valueType.makeNotNull()
            return type.classOrNull?.owner?.displayName ?: type.renderReadable()
        }

    fun field(problem: MockProblem?, site: MockSite.Mocked?, injection: Injection?): MockField =
        MockField(property, annotation, kind, inject, problem, site, injection)
}

private val IrProperty.annotated: Annotated?
    get() {
        if (isFakeOverride) return null
        val kinds = annotations.mapNotNull { annotation ->
            annotation.annotationKind?.takeIf { it in FIELD_KINDS }?.let { annotation to it }
        }
        val inject = kinds.find { (_, kind) -> kind == IntrinsicKind.INJECT_MOCKS }?.first
        val (annotation, kind) = kinds.find { (_, kind) -> kind != IntrinsicKind.INJECT_MOCKS }
            ?: kinds.firstOrNull()
            ?: return null
        return Annotated(this, annotation, kind, inject)
    }

private fun IrProperty.constructsSpy(kind: IntrinsicKind, site: MockSite.Mocked?): Boolean =
    kind == IntrinsicKind.SPY && backingField?.initializer != null && site?.types?.form is MockForm.Construction

internal val IrProperty.constructorParameter: IrValueParameter?
    get() = (backingField?.initializer?.expression as? IrGetValue)
        ?.takeIf { it.origin == IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER }
        ?.symbol?.owner as? IrValueParameter

internal val IrAnnotationContainer.mockAnnotationName: String?
    get() = annotations.find { it.annotationKind in FIELD_KINDS }?.symbol?.owner?.constructedClass?.name?.asString()

internal val IrProperty.valueType: IrType
    get() = backingField?.type ?: getter!!.returnType

private val IrConstructorCall.annotationKind: IntrinsicKind?
    get() = symbol.owner.constructedClass.intrinsicKind
