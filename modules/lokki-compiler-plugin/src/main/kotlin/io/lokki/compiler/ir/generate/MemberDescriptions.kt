package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.CoreFunction
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.irArrayOf
import io.lokki.compiler.ir.irInvoke
import io.lokki.compiler.ir.irJavaClass
import io.lokki.compiler.ir.isAccessibleFrom
import io.lokki.compiler.ir.isPrivateHook
import io.lokki.compiler.ir.redirectedName
import org.jetbrains.kotlin.backend.jvm.ir.getJvmNameFromAnnotation
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.lazy.AbstractFir2IrLazyDeclaration
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
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

internal class MemberDescriptions(lokki: LokkiIrContext) {
    private val context = lokki.pluginContext
    private val symbols = lokki.symbols
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
        if (!declaration.hasDefaultField) flags = flags or BuildConfig.METHOD_NO_DEFAULT_FIELD
        if (member.isPrivateHook || declaration.visibility == DescriptorVisibilities.PROTECTED) {
            flags = flags or BuildConfig.METHOD_PRIVATE_CALL
        }
        val parameters = member.nonDispatchParameters
        return irInvoke(
            symbols[CoreFunction.METHOD_DESCRIPTION],
            irString(member.descriptionName),
            irErasedClass(returnType),
            irInt(flags),
            irErasedClass(declaringClass.defaultType),
            irArrayOf(classClass.starProjectedType, parameters.map { irErasedClass(it.type) }),
            irInt(parameters.indexOfFirst { it.varargElementType != null }),
        )
    }

    private fun IrBuilder.irErasedClass(type: IrType): IrExpression = irJavaClass(symbols, type.erasedClassType())

    private fun IrType.erasedClassType(): IrSimpleType {
        val erased = erasedUpperBound
        if (erased.symbol != builtIns.arrayClass) return erased.symbol.starProjectedType
        val element = (this as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull?.erasedClassType()
        return builtIns.arrayClass.typeWith(element ?: builtIns.anyType)
    }
}

private val IrSimpleFunction.hasDefaultField: Boolean
    get() {
        val property = correspondingPropertySymbol?.owner ?: return true
        if (parentAsClass.redirectedName != null) return true
        val compiled = (property as? AbstractFir2IrLazyDeclaration<*>)?.fir as? FirProperty
            ?: return property.backingField != null && origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        val accessor = (this as? AbstractFir2IrLazyDeclaration<*>)?.fir as? FirPropertyAccessor
        return compiled.hasBackingField && (accessor == null || accessor is FirDefaultPropertyAccessor)
    }

private val IrSimpleFunction.descriptionName: String
    get() {
        getJvmNameFromAnnotation()?.let { return it.substringBefore(BuildConfig.PRIVATE_HOOK_INFIX) }
        val property = correspondingPropertySymbol?.owner
            ?: return name.asString().substringBefore(BuildConfig.PRIVATE_HOOK_INFIX)
        val name = property.name.asString()
        return if (property.getter == this) JvmAbi.getterName(name) else JvmAbi.setterName(name)
    }
