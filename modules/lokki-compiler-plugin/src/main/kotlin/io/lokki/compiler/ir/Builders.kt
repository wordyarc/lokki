package io.lokki.compiler.ir

import io.lokki.compiler.CoreConstructor
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrGeneratorContext
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irAnnotation
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObjectValue
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrVarargElement
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.SpecialNames

internal fun IrBuilder.irArrayOf(elementType: IrType, elements: List<IrVarargElement>): IrExpression {
    val builtIns = context.irBuiltIns
    return irCall(builtIns.arrayOf, builtIns.arrayClass.typeWith(elementType), listOf(elementType)).apply {
        arguments[0] = irVararg(elementType, elements)
    }
}

internal fun IrBuilder.irVararg(elementType: IrType, elements: List<IrVarargElement>): IrExpression =
    IrVarargImpl(startOffset, endOffset, context.irBuiltIns.arrayClass.typeWith(elementType), elementType, elements)

internal fun IrBuilder.irGetObject(objectClass: IrClass): IrExpression =
    irGetObjectValue(objectClass.defaultType, objectClass.symbol)

internal fun IrBuilder.irInvoke(
    function: IrSimpleFunction,
    vararg arguments: IrExpression?,
    receiver: IrExpression? = null,
    type: IrType = function.returnType,
    typeArguments: List<IrType> = emptyList(),
): IrCall =
    irCall(function.symbol, type).apply {
        typeArguments.forEachIndexed { index, argument -> this.typeArguments[index] = argument }
        function.dispatchReceiverParameter?.let { dispatch ->
            this.arguments[dispatch] = receiver ?: function.parentAsClass.let { owner ->
                check(owner.isObject) { "${function.render()} needs a receiver" }
                irGetObject(owner)
            }
        }
        putArguments(function, arguments)
    }

internal fun IrBuilder.irInvoke(constructor: IrConstructor, vararg arguments: IrExpression?): IrConstructorCall =
    irCallConstructor(constructor.symbol, emptyList()).apply { putArguments(constructor, arguments) }

private fun IrFunctionAccessExpression.putArguments(function: IrFunction, arguments: Array<out IrExpression?>) {
    val parameters = function.nonDispatchParameters
    check(parameters.size == arguments.size) {
        "${arguments.size} arguments are passed to ${function.render()}, which takes ${parameters.size}"
    }
    parameters.forEachIndexed { index, parameter -> this.arguments[parameter] = arguments[index] }
}

internal fun IrBuilder.irCompanionCall(function: IrSimpleFunction, block: IrSimpleFunction): IrExpression =
    irInvoke(
        function,
        block.toLambda(context.irBuiltIns),
        type = block.returnType,
        typeArguments = listOf(block.returnType),
    )

internal fun IrGeneratorContext.buildLambda(
    startOffset: Int,
    endOffset: Int,
    returnType: IrType,
    isSuspend: Boolean = false,
): IrSimpleFunction =
    irFactory.buildFun {
        this.startOffset = startOffset
        this.endOffset = endOffset
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        name = SpecialNames.ANONYMOUS
        visibility = DescriptorVisibilities.LOCAL
        this.returnType = returnType
        this.isSuspend = isSuspend
    }

internal fun IrSimpleFunction.toLambda(builtIns: IrBuiltIns): IrExpression {
    val function = if (isSuspend) builtIns.suspendFunctionN(parameters.size) else builtIns.functionN(parameters.size)
    val type = function.typeWith(parameters.map { it.type } + returnType)
    return IrFunctionExpressionImpl(startOffset, endOffset, type, this, IrStatementOrigin.LAMBDA)
}

internal fun IrBuilderWithScope.irHidden(symbols: LokkiSymbols, message: String): IrAnnotation =
    irAnnotation(symbols.deprecated).apply {
        arguments[0] = irString(message)
        arguments[2] = IrGetEnumValueImpl(
            startOffset,
            endOffset,
            symbols.hiddenDeprecation.parentAsClass.defaultType,
            symbols.hiddenDeprecation.symbol,
        )
    }

internal fun IrBuilder.irCast(argument: IrExpression, type: IrType): IrExpression {
    val readOnly = (type as? IrSimpleType)?.readOnlyCollection(context.irBuiltIns) ?: return irAs(argument, type)
    return irImplicitCast(irAs(argument, readOnly), type)
}

private fun IrSimpleType.readOnlyCollection(builtIns: IrBuiltIns): IrSimpleType? {
    val readOnly = when (classOrNull) {
        builtIns.mutableIterableClass -> builtIns.iterableClass
        builtIns.mutableCollectionClass -> builtIns.collectionClass
        builtIns.mutableListClass -> builtIns.listClass
        builtIns.mutableSetClass -> builtIns.setClass
        builtIns.mutableMapClass -> builtIns.mapClass
        builtIns.mutableMapEntryClass -> builtIns.mapEntryClass
        builtIns.mutableIteratorClass -> builtIns.iteratorClass
        builtIns.mutableListIteratorClass -> builtIns.listIteratorClass
        else -> return null
    }
    return buildSimpleType {
        classifier = readOnly
        annotations = emptyList()
    }
}

internal fun IrBuilder.irArrayGet(symbols: LokkiSymbols, array: IrValueDeclaration, index: Int): IrExpression =
    irCall(symbols.arrayGet.symbol, context.irBuiltIns.anyNType).apply {
        arguments[0] = irGet(array)
        arguments[1] = irInt(index)
    }

internal fun IrBuilder.irJavaClass(symbols: LokkiSymbols, type: IrSimpleType): IrExpression {
    val javaClass = symbols.javaClass
    return irCall(javaClass.symbol, javaClass.returnType.classOrFail.typeWith(type), listOf(type)).apply {
        arguments[0] = IrClassReferenceImpl(
            startOffset,
            endOffset,
            context.irBuiltIns.kClassClass.typeWith(type),
            type.classOrFail,
            type,
        )
    }
}

internal fun IrBuilderWithScope.irThrowLokkiException(symbols: LokkiSymbols, message: String): IrExpression =
    irThrow(irInvoke(symbols[CoreConstructor.LOKKI_EXCEPTION], irString(message), irNull()))
