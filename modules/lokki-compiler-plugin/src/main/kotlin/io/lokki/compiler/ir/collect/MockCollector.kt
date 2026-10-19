package io.lokki.compiler.ir.collect

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class MockClassUsage(
    val firstCall: IrCall,
    val mockedType: IrSimpleType,
    var visibility: DescriptorVisibility,
)

internal fun IrFile.collectMockClasses(): Map<MockClassKey, MockClassUsage> =
    MockCollector(this).also(::acceptChildrenVoid).mockClasses

private class MockCollector(private val file: IrFile) : IrVisitorVoid() {
    val mockClasses = LinkedHashMap<MockClassKey, MockClassUsage>()

    private var enclosingInlineFunctions = 0

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        if (declaration.isInline) enclosingInlineFunctions++
        super.visitSimpleFunction(declaration)
        if (declaration.isInline) enclosingInlineFunctions--
    }

    override fun visitCall(expression: IrCall) {
        expression.mockableType(file)?.let { type ->
            val usage = mockClasses.getOrPut(type.mockClassKey(file)) {
                MockClassUsage(expression, type, DescriptorVisibilities.PRIVATE)
            }
            if (enclosingInlineFunctions > 0) usage.visibility = DescriptorVisibilities.PUBLIC
        }
        super.visitCall(expression)
    }
}
