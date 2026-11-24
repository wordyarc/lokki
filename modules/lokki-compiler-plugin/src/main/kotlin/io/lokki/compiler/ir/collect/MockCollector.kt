package io.lokki.compiler.ir.collect

import io.lokki.compiler.ARRAY_OF
import io.lokki.compiler.CoreClass
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.STUB_BLOCKS
import io.lokki.compiler.ir.ConstructorRedirect
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.blockCalls
import io.lokki.compiler.ir.blockStatementCalls
import io.lokki.compiler.ir.constructorClasses
import io.lokki.compiler.ir.fileClassName
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.isVisibleFrom
import io.lokki.compiler.ir.links
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.staticClassArguments
import io.lokki.compiler.ir.staticFileArguments
import io.lokki.compiler.ir.staticReferences
import io.lokki.compiler.ir.unwrapTemporary
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(
    val firstUse: IrElement,
    val mockedTypes: MockedTypes,
    val visibility: DescriptorVisibility,
    val strictOnly: Boolean = false,
) {
    val relaxedChildren: Boolean
        get() = mockedTypes.chained
}

internal class ConstructedSite(
    val redirect: ConstructorRedirect,
    val representative: MockSite.Mocked,
    val instances: Map<IrConstructor, MockSite.Mocked>,
)

internal class MockSites(
    val calls: Map<IrCall, MockSite>,
    val links: Map<IrCall, MockSite.Mocked>,
    val classes: Map<MockClassKey, MockClassUsage>,
    val blockStatementCalls: Set<IrCall>,
    val constructed: Map<IrClass, ConstructedSite>,
    val reifiedCalls: Map<IrCall, List<ReifiedType>>,
)

internal class ReifiedType(val type: IrType, val site: MockSite.Mocked)

internal fun FileContext.collectMockSites(): MockSites {
    val collector = MockCollector(this)
    ir.acceptChildrenVoid(collector)
    collector.addParameters()
    return MockSites(
        collector.calls,
        collector.links,
        collector.usages(),
        collector.blockStatementCalls,
        collector.constructed,
        collector.reifiedCalls,
    )
}

private class MockCollector(private val file: FileContext) : IrVisitorVoid() {
    val calls = LinkedHashMap<IrCall, MockSite>()

    val links = HashMap<IrCall, MockSite.Mocked>()

    val blockStatementCalls = HashSet<IrCall>()

    val constructed = LinkedHashMap<IrClass, ConstructedSite>()

    val reifiedCalls = HashMap<IrCall, List<ReifiedType>>()

    private val classes = LinkedHashMap<MockClassKey, Usage>()

    private var enclosingInlineFunctions = 0

    private var enclosingChainedMocks = if (file.chainsMocks) 1 else 0

    private class Usage(val firstUse: IrElement, val mockedTypes: MockedTypes) {
        var visibility: DescriptorVisibility = DescriptorVisibilities.PRIVATE

        var strictOnly = true
    }

    fun usages(): Map<MockClassKey, MockClassUsage> =
        classes.mapValues { (_, usage) ->
            MockClassUsage(usage.firstUse, usage.mockedTypes, usage.visibility, usage.strictOnly)
        }

    fun addParameters() {
        for (field in file.fields.parameterGroups.flatMap { it.parameters }) {
            val site = field.site?.takeIf { field.problem == null } ?: continue
            add(field.parameter, site)
        }
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitDeclaration(declaration: IrDeclarationBase) {
        val chains = declaration.hasAnnotation(CoreClass.CHAINED_MOCKS.id)
        if (chains) enclosingChainedMocks++
        super.visitDeclaration(declaration)
        if (chains) enclosingChainedMocks--
    }

    private fun MockSite.Mocked.chainedIf(condition: Boolean): MockSite.Mocked =
        if (condition && enclosingChainedMocks > 0) chained() else this

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.isInline) enclosingInlineFunctions++
        super.visitSimpleFunction(declaration)
        if (declaration.isInline) enclosingInlineFunctions--
    }

    override fun visitProperty(declaration: IrProperty) {
        val field = file.fields.fields[declaration]?.takeIf { it.problem == null }
        field?.site?.let { site -> add(declaration, site) }
        (file.external.fields[declaration]?.site as? MockSite.Mocked)?.let { site -> add(declaration, site) }
        super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall) {
        val kind = expression.intrinsicKind
        addReifiedTypes(expression)
        if (kind == IntrinsicKind.MOCK || kind == IntrinsicKind.SPY) {
            val site = when (expression.reifiedMockParameter) {
                null -> file.mockability.site(expression.request(kind == IntrinsicKind.SPY))
                else -> MockSite.Reified
            }
            val relaxed = kind == IntrinsicKind.MOCK && expression.mayBeRelaxed
            val used = (site as? MockSite.Mocked)?.chainedIf(relaxed) ?: site
            calls[expression] = used
            if (used is MockSite.Mocked) add(expression, used, strict = kind == IntrinsicKind.MOCK && !relaxed)
        }
        val redirected = when (kind) {
            IntrinsicKind.MOCK_OBJECT -> expression.mockedObjects.orEmpty().mapNotNull(file.redirects::of)
            IntrinsicKind.MOCK_STATIC -> expression.staticReferences.orEmpty().mapNotNull(file.redirects::static) +
                expression.staticFileArguments.orEmpty().mapNotNull { it.fileClassName }.flatMap(file.redirects::file) +
                expression.staticClassArguments.orEmpty().mapNotNull(file.redirects::ofStaticClass)
            else -> emptyList()
        }
        for (redirect in redirected) add(expression, redirect.site)
        if (kind == IntrinsicKind.MOCK_CONSTRUCTOR) expression.constructorClasses.orEmpty().forEach { addConstructed(expression, it) }
        if (kind in STUB_BLOCKS) {
            for (stubbed in expression.blockCalls()) {
                file.redirects.member(stubbed.call.symbol.owner)?.let { add(expression, it.redirect.site) }
            }
        }
        blockStatementCalls += expression.blockStatementCalls()
        for (link in expression.blockCalls().flatMap { it.receiver?.links().orEmpty() }) {
            val site = file.mockability.child(link.call.type)?.chainedIf(true) ?: continue
            links[link.call] = site
            add(link.call, site)
        }
        super.visitCall(expression)
    }

    private fun addReifiedTypes(call: IrCall) {
        val types = file.reifiedHelpers.concreteTypes(call).mapNotNull { type ->
            when (val site = file.mockability.reified(type)) {
                is MockSite.Mocked -> {
                    val chained = site.chainedIf(true)
                    add(call, chained)
                    ReifiedType(type, chained)
                }
                is MockSite.Rejected -> null.also { file.diagnostics.report(call, site.problem) }
                MockSite.Reified -> null
            }
        }
        if (types.isNotEmpty()) reifiedCalls[call] = types
    }

    private fun addConstructed(use: IrCall, target: IrClass) {
        if (target in constructed) return
        val redirect = file.redirects.constructors(target) ?: return
        val constructors = redirect.constructors.filter { it.isVisibleFrom(file.ir) }
        if (constructors.isEmpty()) return
        val type = target.symbol.starProjectedType
        val representative = file.mockability.site(MockRequest(false, type, null, emptyList(), "")) as? MockSite.Mocked
            ?: return
        add(use, representative)
        val instances = constructors.associateWith { constructor ->
            val form = MockForm.Construction(constructor, constructor.parameters.map { true })
            val site = file.mockability.site(MockRequest(true, type, null, emptyList(), "", form)) as? MockSite.Mocked
                ?: return
            add(use, site)
            site
        }
        constructed[target] = ConstructedSite(redirect, representative, instances)
    }

    private fun add(use: IrElement, site: MockSite.Mocked, strict: Boolean = false): Usage {
        val usage = classes.getOrPut(site.key) { Usage(use, site.types) }
        if (enclosingInlineFunctions > 0) usage.visibility = DescriptorVisibilities.PUBLIC
        usage.strictOnly = usage.strictOnly && strict
        return usage
    }
}

internal val IrCall.spiedInstance: IrExpression?
    get() = argument(ParameterNames.SPIED_INSTANCE)

private fun IrCall.request(isSpy: Boolean): MockRequest =
    MockRequest(isSpy, typeArguments.single()!!, spiedInstance, moreInterfaces(), "'${ParameterNames.MORE_INTERFACES}'")

private val IrCall.mayBeRelaxed: Boolean
    get() = argument(ParameterNames.RELAXED)?.let { (it as? IrConst)?.value != false } == true

private fun IrCall.moreInterfaces(): List<AddedInterface>? =
    when (val argument = argument(ParameterNames.MORE_INTERFACES)?.unwrapTemporary()) {
        null -> emptyList()
        is IrVararg -> argument.classLiterals()
        else -> null
    }

private fun IrVararg.classLiterals(): List<AddedInterface>? =
    elements.flatMap { element ->
        when (element) {
            is IrClassReference -> listOf(AddedInterface(element.classType, element))
            is IrSpreadElement -> {
                val array = (element.expression.unwrapTemporary() as? IrCall)
                    ?.takeIf { it.symbol.owner.kotlinFqName == ARRAY_OF.asSingleFqName() }
                (array?.arguments?.singleOrNull() as? IrVararg)?.classLiterals() ?: return null
            }
            else -> return null
        }
    }
