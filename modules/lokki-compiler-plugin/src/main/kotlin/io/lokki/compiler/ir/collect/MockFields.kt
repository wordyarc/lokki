package io.lokki.compiler.ir.collect

import io.lokki.compiler.FIELD_KINDS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.annotationClass
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.renderReadable
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockField(
    val property: IrProperty,
    val annotation: IrAnnotation,
    val kind: IntrinsicKind,
    val inject: IrAnnotation?,
    val problem: MockProblem?,
    val site: MockSite.Mocked?,
    val injection: Injection?,
) {
    val name: String
        get() = annotation.mockName ?: property.name.asString()

    val isConstructedSpy: Boolean = property.constructsSpy(kind, site)

    val isInitialized: Boolean
        get() = problem == null && (inject != null || !isConstructedSpy)
}

internal class MockParameterField(
    val parameter: IrValueParameter,
    val index: Int,
    val annotation: IrAnnotation,
    val kind: IntrinsicKind,
    val problem: MockProblem?,
    val site: MockSite.Mocked?,
    val injection: Injection?,
) {
    val name: String
        get() = annotation.mockName ?: parameter.name.asString()
}

internal class MockParameterGroup(val function: IrFunction, val parameters: List<MockParameterField>)

internal class MockFields(
    val fields: Map<IrProperty, MockField>,
    val owners: Map<IrClass, ClassMockFields>,
    val parameterGroups: List<MockParameterGroup>,
    val unsupportedParameters: List<IrValueParameter>,
)

internal class ClassMockFields(val fields: List<MockField>, val injected: List<MockField>)

internal fun IrFile.collectMockFields(mockability: Mockability, typeSystem: IrTypeSystemContext): MockFields {
    val collector = MockFieldCollector(mockability, InjectionPlanner(this, typeSystem))
    acceptChildrenVoid(collector)
    return MockFields(collector.fields, collector.owners, collector.parameterGroups, collector.unsupportedParameters)
}

private class MockFieldCollector(
    private val mockability: Mockability,
    private val planner: InjectionPlanner,
) : IrVisitorVoid() {
    val fields = LinkedHashMap<IrProperty, MockField>()
    val owners = LinkedHashMap<IrClass, ClassMockFields>()
    val parameterGroups = ArrayList<MockParameterGroup>()
    val unsupportedParameters = ArrayList<IrValueParameter>()

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

    override fun visitConstructor(declaration: IrConstructor) {
        collectParameters(declaration)
        super.visitConstructor(declaration)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        collectParameters(declaration)
        super.visitSimpleFunction(declaration)
    }

    private fun collectParameters(function: IrFunction) {
        val annotated = function.parameters.filter { it.mockAnnotationName != null }
        if (annotated.isEmpty()) return
        if (!function.takesMockParameters) {
            unsupportedParameters += annotated
            return
        }
        parameterGroups += resolveParameters(function, annotated)
    }

    private fun resolveParameters(function: IrFunction, annotated: List<IrValueParameter>): MockParameterGroup {
        val regular = function.parameters.filter { it.kind == IrParameterKind.Regular }
        val kinds = annotated.associateWith { it.mockAnnotations!! }
        val requests = annotated.mapNotNull { parameter ->
            val (annotation, kind, inject) = kinds.getValue(parameter)
            (inject ?: annotation.takeIf { kind == IntrinsicKind.SPY })
                ?.let { InjectionRequest(parameter, parameter.type, it) }
        }
        val plan = planner.planParameters(annotated, requests)
        val fields = annotated.map { parameter ->
            val (annotation, kind, inject) = kinds.getValue(parameter)
            val result = plan.results[parameter]
            val injection = result as? Injection
            val type = parameter.type.makeNotNull()
            val conflict = if (inject != null && kind != IntrinsicKind.INJECT_MOCKS && kind != IntrinsicKind.SPY) {
                Unsupported("A parameter cannot have both '@InjectMocks' and '@${annotation.annotationClass!!.name}'")
            } else {
                null
            }
            val failed = (result as? InjectionProblem)?.takeIf { inject != null }
                ?.let { FailedInjection(it, type.classOrNull?.owner?.displayName ?: type.renderReadable()) }
            val mocked = inject == null || kind == IntrinsicKind.SPY
            val site = if (mocked && conflict == null && failed == null) {
                mockability.site(
                    MockRequest(
                        kind == IntrinsicKind.SPY,
                        type,
                        null,
                        listOfNotNull(parameter.additionalInterface),
                        "'@AdditionalInterface'",
                        injection?.construction,
                    )
                ).chainedIf(kind != IntrinsicKind.SPY && function.chainsMocks)
            } else {
                null
            }
            MockParameterField(
                parameter,
                regular.indexOf(parameter),
                annotation,
                kind,
                conflict ?: failed ?: (site as? MockSite.Rejected)?.problem,
                site as? MockSite.Mocked,
                injection,
            )
        }
        return MockParameterGroup(function, fields)
    }

    private fun resolve(owner: IrClass, annotated: List<Annotated>): ClassMockFields {
        val problems = annotated.associateWith { it.declarationProblem() }
        val targets = annotated.filter { it.inject != null && problems[it] == null }
        val plan = planner.plan(owner, targets.map { InjectionRequest(it.property, it.property.valueType, it.inject!!) })
        val fields = annotated.map { field ->
            val result = plan.results[field.property]
            val injection = result as? Injection
            val failed = (result as? InjectionProblem)?.let { FailedInjection(it, field.typeName) }
            val site = if (field.isMock && problems[field] == null && failed == null) {
                mockability.site(field.request(injection))
                    .chainedIf(field.kind != IntrinsicKind.SPY && field.property.chainsMocks)
            } else {
                null
            }
            val problem = problems[field] ?: failed ?: (site as? MockSite.Rejected)?.problem
            val mocked = site as? MockSite.Mocked
            field.field(problem ?: field.immutableProblem(mocked), mocked, injection)
        }
        val byProperty = fields.associateBy { it.property }
        val injected = plan.order.map { byProperty.getValue(it as IrProperty) }.filter { it.problem == null }
        return ClassMockFields(fields, injected)
    }

    private fun Annotated.declarationProblem(): MockProblem? =
        when {
            property.constructorParameter != null ->
                Unsupported("'@$annotationName' on a property of the primary constructor must be on its parameter")
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
            listOfNotNull(property.additionalInterface),
            "'@AdditionalInterface'",
            injection?.takeIf { instance == null }?.construction,
        )
    }
}

private class Annotated(
    val property: IrProperty,
    val annotation: IrAnnotation,
    val kind: IntrinsicKind,
    val inject: IrAnnotation?,
) {
    val annotationName: String
        get() = annotation.annotationClass!!.name.asString()

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
    get() = mockAnnotations?.takeUnless { isFakeOverride }?.let { Annotated(this, it.annotation, it.kind, it.inject) }

private data class MockAnnotations(val annotation: IrAnnotation, val kind: IntrinsicKind, val inject: IrAnnotation?)

private val IrAnnotationContainer.mockAnnotations: MockAnnotations?
    get() {
        val kinds = annotations.mapNotNull { annotation ->
            annotation.annotationKind?.takeIf { it in FIELD_KINDS }?.let { annotation to it }
        }
        val inject = kinds.find { (_, kind) -> kind == IntrinsicKind.INJECT_MOCKS }?.first
        val (annotation, kind) = kinds.find { (_, kind) -> kind != IntrinsicKind.INJECT_MOCKS }
            ?: kinds.firstOrNull()
            ?: return null
        return MockAnnotations(annotation, kind, inject)
    }

private val IrAnnotationContainer.additionalInterface: AddedInterface?
    get() = annotations.find { it.annotationKind == IntrinsicKind.ADDITIONAL_INTERFACE }
        ?.let { it.argument(ParameterNames.INTERFACE_TYPE) as? IrClassReference }
        ?.let { AddedInterface(it.classType, it) }

private val IrAnnotation.mockName: String?
    get() = ((argument(ParameterNames.NAME) as? IrConst)?.value as? String)?.takeIf { it.isNotBlank() }

private fun MockSite.chainedIf(condition: Boolean): MockSite =
    if (condition && this is MockSite.Mocked) chained() else this

private fun IrProperty.constructsSpy(kind: IntrinsicKind, site: MockSite.Mocked?): Boolean =
    kind == IntrinsicKind.SPY && backingField?.initializer != null && site?.types?.form is MockForm.Construction

internal val IrProperty.constructorParameter: IrValueParameter?
    get() = (backingField?.initializer?.expression as? IrGetValue)
        ?.takeIf { it.origin == IrStatementOrigin.INITIALIZE_PROPERTY_FROM_PARAMETER }
        ?.symbol?.owner as? IrValueParameter

internal val IrAnnotationContainer.mockAnnotationName: String?
    get() = annotations.find { it.annotationKind in FIELD_KINDS }?.annotationClass?.name?.asString()

private val IrFunction.takesMockParameters: Boolean
    get() {
        val owner = parent as? IrClass ?: return false
        if (owner.isLocal) return false
        return this is IrConstructor || this is IrSimpleFunction && correspondingPropertySymbol == null &&
            dispatchReceiverParameter != null
    }

internal val IrProperty.valueType: IrType
    get() = backingField?.type ?: getter!!.returnType

private val IrAnnotation.annotationKind: IntrinsicKind?
    get() = annotationClass?.intrinsicKind
