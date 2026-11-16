package io.lokki.compiler.ir.collect

import io.lokki.compiler.ARRAY_OF
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.FileContext
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.blockCalls
import io.lokki.compiler.ir.blockStatementCalls
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.links
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.staticReferences
import io.lokki.compiler.ir.unwrapTemporary
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(
    val firstUse: IrElement,
    val mockedTypes: MockedTypes,
    val visibility: DescriptorVisibility,
    val relaxedChildren: Boolean,
)

internal class MockSites(
    val calls: Map<IrCall, MockSite>,
    val classes: Map<MockClassKey, MockClassUsage>,
    val blockStatementCalls: Set<IrCall>,
)

internal fun FileContext.collectMockSites(): MockSites {
    val collector = MockCollector(this)
    ir.acceptChildrenVoid(collector)
    return MockSites(collector.calls, collector.usages(), collector.blockStatementCalls)
}

private class MockCollector(private val file: FileContext) : IrVisitorVoid() {
    val calls = HashMap<IrCall, MockSite>()

    val blockStatementCalls = HashSet<IrCall>()

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
            IntrinsicKind.MOCK_STATIC -> expression.staticReferences.orEmpty().mapNotNull(file.redirects::static)
            else -> emptyList()
        }
        for (redirect in redirected) add(expression, file.mockability.mock(redirect.redirect.defaultType))
        blockStatementCalls += expression.blockStatementCalls()
        if (file.chainedMocks) {
            for (link in expression.blockCalls().flatMap { it.receiver?.links().orEmpty() }) {
                file.mockability.child(link.call.type)?.let { add(link.call, it) }
            }
        }
        super.visitCall(expression)
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
