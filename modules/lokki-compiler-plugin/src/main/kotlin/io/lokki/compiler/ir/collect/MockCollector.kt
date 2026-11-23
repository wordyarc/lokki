package io.lokki.compiler.ir.collect

import io.lokki.compiler.ARRAY_OF
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
import io.lokki.compiler.ir.staticFileArguments
import io.lokki.compiler.ir.staticReferences
import io.lokki.compiler.ir.unwrapTemporary
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(
    val firstUse: IrElement,
    val mockedTypes: MockedTypes,
    val visibility: DescriptorVisibility,
    val relaxedChildren: Boolean,
)

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
)

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
    )
}

private class MockCollector(private val file: FileContext) : IrVisitorVoid() {
    val calls = LinkedHashMap<IrCall, MockSite>()

    val links = HashMap<IrCall, MockSite.Mocked>()

    val blockStatementCalls = HashSet<IrCall>()

    val constructed = LinkedHashMap<IrClass, ConstructedSite>()

    private val classes = LinkedHashMap<MockClassKey, Usage>()

    private var enclosingInlineFunctions = 0

    private class Usage(val firstUse: IrElement, val mockedTypes: MockedTypes) {
        var visibility: DescriptorVisibility = DescriptorVisibilities.PRIVATE

        var relaxedChildren = false
    }

    fun usages(): Map<MockClassKey, MockClassUsage> =
        classes.mapValues { (_, usage) ->
            MockClassUsage(usage.firstUse, usage.mockedTypes, usage.visibility, usage.relaxedChildren)
        }

    fun addParameters() {
        for (field in file.fields.parameterGroups.flatMap { it.parameters }) {
            val site = field.site?.takeIf { field.problem == null } ?: continue
            val usage = add(field.parameter, site)
            if (file.chainedMocks && field.kind != IntrinsicKind.SPY) usage.relaxedChildren = true
        }
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.isInline) enclosingInlineFunctions++
        super.visitSimpleFunction(declaration)
        if (declaration.isInline) enclosingInlineFunctions--
    }

    override fun visitProperty(declaration: IrProperty) {
        val field = file.fields.fields[declaration]?.takeIf { it.problem == null }
        field?.site?.let { site ->
            val usage = add(declaration, site)
            if (file.chainedMocks && field.kind != IntrinsicKind.SPY) usage.relaxedChildren = true
        }
        super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall) {
        val kind = expression.intrinsicKind
        if (kind == IntrinsicKind.MOCK || kind == IntrinsicKind.SPY) {
            val site = file.mockability.site(expression.request(kind == IntrinsicKind.SPY))
            calls[expression] = site
            if (site is MockSite.Mocked) {
                val usage = add(expression, site)
                val relaxed = kind == IntrinsicKind.MOCK && expression.mayBeRelaxed
                if (file.chainedMocks && relaxed) usage.relaxedChildren = true
            }
        }
        val redirected = when (kind) {
            IntrinsicKind.MOCK_OBJECT -> expression.mockedObjects.orEmpty().mapNotNull(file.redirects::of)
            IntrinsicKind.MOCK_STATIC -> expression.staticReferences.orEmpty().mapNotNull(file.redirects::static) +
                expression.staticFileArguments.orEmpty().mapNotNull { it.fileClassName }.flatMap(file.redirects::file)
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
            val site = file.mockability.child(link.call.type) ?: continue
            links[link.call] = site
            add(link.call, site)
        }
        super.visitCall(expression)
    }

    private fun addConstructed(use: IrCall, target: IrClass) {
        if (target in constructed) return
        val redirect = file.redirects.constructors(target) ?: return
        val type = target.symbol.starProjectedType
        val representative = file.mockability.site(MockRequest(false, type, null, emptyList(), "")) as? MockSite.Mocked
            ?: return
        add(use, representative)
        val instances = redirect.constructors.filter { it.isVisibleFrom(file.ir) }.associateWith { constructor ->
            val form = MockForm.Construction(constructor, constructor.parameters.map { true })
            val site = file.mockability.site(MockRequest(true, type, null, emptyList(), "", form)) as? MockSite.Mocked
                ?: return
            add(use, site)
            site
        }
        constructed[target] = ConstructedSite(redirect, representative, instances)
    }

    private fun add(use: IrElement, site: MockSite.Mocked): Usage {
        val usage = classes.getOrPut(site.key) { Usage(use, site.types) }
        if (enclosingInlineFunctions > 0) usage.visibility = DescriptorVisibilities.PUBLIC
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
