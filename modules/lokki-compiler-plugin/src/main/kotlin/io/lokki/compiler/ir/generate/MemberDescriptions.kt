package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.ir.LokkiSymbols
import io.lokki.compiler.ir.irGetObject
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.isAccessibleFrom
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.ir.getJvmNameFromAnnotation
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.erasedUpperBound
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.StandardClassIds

internal class MemberDescriptions(private val context: IrPluginContext, private val symbols: LokkiSymbols) {
    private val builtIns = context.irBuiltIns
    private val classClass = symbols.javaClass.returnType.classOrFail

    fun IrBuilder.irDescription(member: IrSimpleFunction, file: IrFile): IrExpression {
        val overridden = member.overriddenSymbols.map { it.owner }
        val declaration = overridden.firstNotNullOfOrNull { it.resolveFakeOverride() }
            ?: overridden.first().resolveFakeOverrideMaybeAbstractOrFail()
        val declaringClass = declaration.parentAsClass.takeIf { it.isAccessibleFrom(file) }
            ?: overridden.first().parentAsClass
        val returnType = member.returnType
        val platform = returnType.hasAnnotation(StandardClassIds.Annotations.FlexibleNullability)
        var flags = 0
        if (returnType.isMarkedNullable() && !platform) flags = flags or BuildConfig.METHOD_RETURN_TYPE_NULLABLE
        if (returnType.isUnit()) flags = flags or BuildConfig.METHOD_RETURNS_UNIT
        if (returnType.isNothing()) flags = flags or BuildConfig.METHOD_RETURNS_NOTHING
        if (member.isSuspend) flags = flags or BuildConfig.METHOD_SUSPEND
        if (declaration.parentAsClass.isSubclassOf(builtIns.functionClass.owner)) {
            flags = flags or BuildConfig.METHOD_FN_CALL
        }
        val parameters = member.nonDispatchParameters
        return irCall(symbols.methodDescription).apply {
            arguments[0] = irGetObject(symbols.methodDescriptions)
            arguments[1] = irString(member.jvmName)
            arguments[2] = irJavaClass(returnType)
            arguments[3] = irInt(flags)
            arguments[4] = irJavaClass(declaringClass.defaultType)
            arguments[5] = irArrayOf(classClass.starProjectedType, parameters.map { irJavaClass(it.type) })
            arguments[6] = irInt(parameters.indexOfFirst { it.varargElementType != null })
        }
    }

    private fun IrBuilder.irJavaClass(type: IrType): IrExpression {
        val classType = type.erasedClassType()
        return irCall(symbols.javaClass.symbol, classClass.typeWith(classType), listOf(classType)).apply {
            arguments[0] = IrClassReferenceImpl(
                startOffset,
                endOffset,
                builtIns.kClassClass.typeWith(classType),
                classType.classOrFail,
                classType,
            )
        }
    }

    private fun IrType.erasedClassType(): IrSimpleType {
        val erased = erasedUpperBound
        if (erased.symbol != builtIns.arrayClass) return erased.symbol.starProjectedType
        val element = (this as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull?.erasedClassType()
        return builtIns.arrayClass.typeWith(element ?: builtIns.anyType)
    }
}

private val IrSimpleFunction.jvmName: String
    get() {
        getJvmNameFromAnnotation()?.let { return it }
        val property = correspondingPropertySymbol?.owner ?: return name.asString()
        val name = property.name.asString()
        return if (property.getter == this) JvmAbi.getterName(name) else JvmAbi.setterName(name)
    }
