package io.lokki.compiler.ir.collect

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(val firstCall: IrCall, var visibility: DescriptorVisibility)

internal fun IrFile.collectMockClasses(): Map<MockClassKey, MockClassUsage> =
    MockCollector(this).also(::acceptChildrenVoid).mockClasses

private class MockCollector(private val file: IrFile) : IrVisitorVoid() {
    val mockClasses = LinkedHashMap<MockClassKey, MockClassUsage>()

    private var enclosingExposedInlineFunctions = 0

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        val exposed = declaration.isInline && !DescriptorVisibilities.isPrivate(declaration.visibility)
        if (exposed) enclosingExposedInlineFunctions++
        super.visitSimpleFunction(declaration)
        if (exposed) enclosingExposedInlineFunctions--
    }

    override fun visitCall(expression: IrCall) {
        expression.mockClassKey(file)?.let { key ->
            val usage = mockClasses.getOrPut(key) { MockClassUsage(expression, DescriptorVisibilities.PRIVATE) }
            if (enclosingExposedInlineFunctions > 0) usage.visibility = DescriptorVisibilities.PUBLIC
        }
        super.visitCall(expression)
    }
}
