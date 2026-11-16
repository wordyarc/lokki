package io.lokki.compiler.ir.lower

import io.lokki.compiler.LokkiPluginKey
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.contains
import io.lokki.compiler.ir.irGetObject
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class StaticInitializers(private val context: IrPluginContext, private val symbols: LokkiSymbols) {
    private val generated = IrDeclarationOrigin.GeneratedByPlugin(LokkiPluginKey)

    fun lower(file: IrFile) {
        for (declaration in file.declarations) lower(declaration, static = true)
    }

    private fun lower(declaration: IrDeclaration, static: Boolean) {
        when (declaration) {
            is IrClass -> if (declaration.origin != generated) {
                for (member in declaration.declarations) lower(member, declaration.kind == ClassKind.OBJECT)
            }
            is IrProperty -> if (static) lower(declaration)
            is IrAnonymousInitializer -> if (static) declaration.body.bracket(declaration.symbol, null)
            else -> {}
        }
    }

    private fun lower(property: IrProperty) {
        val field = property.backingField ?: return
        val initializer = field.initializer ?: return
        if (property.isDelegated) initializer.expression.transformChildrenVoid(DelegateLambdas())
        if (!initializer.expression.contains { it is IrFunctionAccessExpression }) return
        val expression = initializer.expression
        val builder = DeclarationIrBuilder(context, field.symbol, expression.startOffset, expression.endOffset)
        initializer.expression = builder.irBracket(expression, expression.type)
    }

    private fun IrBlockBody.bracket(owner: IrSymbol, function: IrSimpleFunction?) {
        val builder = DeclarationIrBuilder(context, owner, startOffset, endOffset)
        val type = function?.returnType ?: context.irBuiltIns.unitType
        val block = builder.irBlock(resultType = type) { statements.forEach { +it } }
        statements.clear()
        statements += builder.irBracket(block, type).let { if (function == null) it else builder.irReturn(it) }
    }

    private fun IrBuilderWithScope.irBracket(expression: IrExpression, type: IrType): IrExpression =
        irBlock(resultType = type) {
            +irStaticInitializerCall(symbols.enterStaticInitializer)
            +irTry(type, expression, emptyList(), irStaticInitializerCall(symbols.exitStaticInitializer))
        }

    private fun IrBuilderWithScope.irStaticInitializerCall(function: IrSimpleFunction): IrExpression =
        irCall(function).apply {
            arguments[0] = irGetObject(symbols.staticInitializer)
        }

    private inner class DelegateLambdas : IrElementTransformerVoid() {
        override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
            val function = expression.function
            (function.body as? IrBlockBody)?.bracket(function.symbol, function)
            return expression
        }
    }
}
