package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.util.isFromJava
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.types.Variance

internal val IrSimpleFunction.memberKeys: List<String>
    get() = roots().map { it.memberKey }

internal val IrSimpleFunction.root: IrSimpleFunction
    get() = roots().first()

private fun IrSimpleFunction.roots(): List<IrSimpleFunction> =
    if (overriddenSymbols.isEmpty()) listOf(this) else overriddenSymbols.flatMap { it.owner.roots() }.distinct()

private val IrSimpleFunction.memberKey: String
    get() = buildString {
        val nullability = !isFromJava()
        append(parentAsClass.kotlinFqName.asString()).append('#')
        val context = parameters.filter { it.kind == IrParameterKind.Context }
        if (context.isNotEmpty()) context.joinTo(this, ",", "context(", ")") { it.key(nullability) }
        if (typeParameters.isNotEmpty()) {
            typeParameters.joinTo(this, ",", "<", ">") { parameter ->
                parameter.superTypes.joinToString("&", "#${parameter.position}:") { it.key(nullability) }
            }
        }
        val receiver = parameters.find { it.kind == IrParameterKind.ExtensionReceiver }
        if (receiver != null) append(receiver.key(nullability)).append('.')
        append(name.asString())
        parameters.filter { it.kind == IrParameterKind.Regular }.joinTo(this, ",", "(", ")") { it.key(nullability) }
    }

private fun IrValueParameter.key(nullability: Boolean): String =
    varargElementType?.let { "vararg ${it.key(nullability)}" } ?: type.key(nullability)

private fun IrType.key(nullability: Boolean): String =
    if (this !is IrSimpleType) render() else buildString {
        when (val classifier = classifier) {
            is IrClassSymbol -> append(classifier.owner.kotlinFqName.asString())
            is IrTypeParameterSymbol -> append('#').append(classifier.owner.position)
            else -> append(render())
        }
        if (arguments.isNotEmpty()) {
            arguments.joinTo(this, ",", "<", ">") { argument ->
                when (argument) {
                    is IrStarProjection -> "*"
                    is IrTypeProjection -> argument.variance.prefix + argument.type.key(nullability)
                }
            }
        }
        if (nullability && isMarkedNullable()) append('?')
    }

private val IrTypeParameter.position: Int
    get() = when (val container = parent) {
        is IrSimpleFunction -> (container.parent as? IrClass)?.typeParameters.orEmpty().size + index
        else -> index
    }

private val Variance.prefix: String
    get() = if (this == Variance.INVARIANT) "" else "$label "
