package io.lokki.compiler.ir.collect

import io.lokki.compiler.CHAINED_MOCKS
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.blockCalls
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.Redirects
import io.lokki.compiler.ir.links
import io.lokki.compiler.ir.mockedObjects
import io.lokki.compiler.ir.staticReferences
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(
    val firstUse: IrElement,
    val mockedTypes: MockedTypes,
    var visibility: DescriptorVisibility,
    var relaxedChildren: Boolean = false,
)

internal fun IrFile.collectMockClasses(redirects: Redirects): Map<MockClassKey, MockClassUsage> =
    MockCollector(this, redirects).also(::acceptChildrenVoid).mockClasses

private class MockCollector(private val file: IrFile, private val redirects: Redirects) : IrVisitorVoid() {
    val mockClasses = LinkedHashMap<MockClassKey, MockClassUsage>()

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
        val field = declaration.mockField?.takeIf { it.unsupportedReason(file) == null }
        field?.mockedTypes(file)?.let { types ->
            val usage = add(declaration, types)
            if (chainedMocks && field.kind != IntrinsicKind.SPY) usage.relaxedChildren = true
        }
        super.visitProperty(declaration)
    }

    override fun visitCall(expression: IrCall) {
        expression.mockedTypes(file)?.let { types ->
            val usage = add(expression, types)
            if (chainedMocks && expression.mayBeRelaxed) usage.relaxedChildren = true
        }
        val redirected = when (expression.intrinsicKind) {
            IntrinsicKind.MOCK_OBJECT -> expression.mockedObjects.orEmpty().mapNotNull(redirects::of)
            IntrinsicKind.MOCK_STATIC -> expression.staticReferences.orEmpty().mapNotNull(redirects::static)
            else -> emptyList()
        }
        for (redirect in redirected) {
            add(expression, MockedTypes(redirect.redirect.defaultType, emptyList(), MockForm.Mock))
        }
        if (chainedMocks) {
            for (link in expression.blockCalls().flatMap { it.receiver?.links().orEmpty() }) {
                link.call.type.childMockedTypes(file)?.let { add(link.call, it) }
            }
        }
        super.visitCall(expression)
    }

    private fun add(use: IrElement, types: MockedTypes): MockClassUsage {
        val usage = mockClasses.getOrPut(types.mockClassKey(file)) {
            MockClassUsage(use, types, DescriptorVisibilities.PRIVATE)
        }
        if (enclosingInlineFunctions > 0) usage.visibility = DescriptorVisibilities.PUBLIC
        return usage
    }
}

private val IrCall.mayBeRelaxed: Boolean
    get() = intrinsicKind == IntrinsicKind.MOCK &&
        argument("relaxed")?.let { (it as? IrConst)?.value != false } == true
