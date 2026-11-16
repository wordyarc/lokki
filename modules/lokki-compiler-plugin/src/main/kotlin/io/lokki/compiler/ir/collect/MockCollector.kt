package io.lokki.compiler.ir.collect

import io.lokki.compiler.CHAINED_MOCKS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.ir.Redirects
import io.lokki.compiler.ir.argument
import io.lokki.compiler.ir.blockCalls
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.links
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.staticReferences
import io.lokki.compiler.ir.unwrapTemporary
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName

internal class MockClassUsage(
    val firstUse: IrElement,
    val mockedTypes: MockedTypes,
    var visibility: DescriptorVisibility,
    var relaxedChildren: Boolean = false,
)

internal class MockSites(val calls: Map<IrCall, MockSite>, val classes: Map<MockClassKey, MockClassUsage>)

internal fun IrFile.collectMockSites(mockability: Mockability, fields: MockFields, redirects: Redirects): MockSites {
    val collector = MockCollector(this, mockability, fields, redirects)
    acceptChildrenVoid(collector)
    return MockSites(collector.calls, collector.classes)
}

private class MockCollector(
    file: IrFile,
    private val mockability: Mockability,
    private val fields: MockFields,
    private val redirects: Redirects,
) : IrVisitorVoid() {
    val calls = HashMap<IrCall, MockSite>()
    val classes = LinkedHashMap<MockClassKey, MockClassUsage>()

    private val chainedMocks = file.hasAnnotation(CHAINED_MOCKS)

    private var enclosingInlineFunctions = 0

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.isInline) enclosingInlineFunctions++
        super.visitSimpleFunction(declaration)
        if (declaration.isInline) enclosingInlineFunctions--
    }

    override fun visitProperty(declaration: IrProperty) {
        val field = fields.fields[declaration]?.takeIf { it.problem == null }
        field?.site?.let { site ->
            val usage = add(declaration, site)
            if (chainedMocks && field.kind != IntrinsicKind.SPY) usage.relaxedChildren = true
        }
        super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall) {
        val kind = expression.intrinsicKind
        if (kind == IntrinsicKind.MOCK || kind == IntrinsicKind.SPY) {
            val site = mockability.site(expression.request(kind == IntrinsicKind.SPY))
            calls[expression] = site
            if (site is MockSite.Mocked) {
                val usage = add(expression, site)
                if (chainedMocks && kind == IntrinsicKind.MOCK && expression.mayBeRelaxed) usage.relaxedChildren = true
            }
        }
        val redirected = when (kind) {
            IntrinsicKind.MOCK_OBJECT -> expression.mockedObjects.orEmpty().mapNotNull(redirects::of)
            IntrinsicKind.MOCK_STATIC -> expression.staticReferences.orEmpty().mapNotNull(redirects::static)
            else -> emptyList()
        }
        for (redirect in redirected) add(expression, mockability.mock(redirect.redirect.defaultType))
        if (chainedMocks) {
            for (link in expression.blockCalls().flatMap { it.receiver?.links().orEmpty() }) {
                mockability.child(link.call.type)?.let { add(link.call, it) }
            }
        }
        super.visitCall(expression)
    }

    private fun add(use: IrElement, site: MockSite.Mocked): MockClassUsage {
        val usage = classes.getOrPut(site.key) { MockClassUsage(use, site.types, DescriptorVisibilities.PRIVATE) }
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
                    ?.takeIf { it.symbol.owner.kotlinFqName == ARRAY_OF }
                (array?.arguments?.singleOrNull() as? IrVararg)?.classLiterals() ?: return null
            }
            else -> return null
        }
    }

private val ARRAY_OF = FqName("kotlin.arrayOf")
