package io.lokki.compiler.ir.generate

import io.lokki.compiler.COMPOSABLE_ANNOTATION
import io.lokki.compiler.InterceptionExclusion
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.isClassFinalizer
import io.lokki.compiler.ir.isOverridable
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.isMethodOfAny
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.JvmStandardClassIds

internal fun IrModuleFragment.sourceClasses(): List<IrClass> =
    files.flatMap { file -> file.declarations.flatMap { it.sourceClasses() } }

private fun IrDeclaration.sourceClasses(): List<IrClass> {
    if (this !is IrClass || origin != IrDeclarationOrigin.DEFINED) return emptyList()
    return declarations.flatMap { it.sourceClasses() } + this
}

internal fun IrClass.isMockableBy(mockable: MockablePatterns): Boolean =
    kind == ClassKind.CLASS && !isValue && !isExpect &&
        !hasAnnotation(JvmStandardClassIds.JVM_RECORD_ANNOTATION_CLASS_ID) && matches(mockable)

internal fun IrClass.isConstructibleBy(mockable: MockablePatterns): Boolean =
    isMockableBy(mockable) && !isInner && modality != Modality.ABSTRACT && modality != Modality.SEALED &&
        !isHiddenInFile

internal fun IrClass.isInterceptableBy(mockable: MockablePatterns): Boolean =
    (kind == ClassKind.OBJECT || kind == ClassKind.ENUM_CLASS) && !isExpect && !isHiddenInFile &&
        (matches(mockable) || isCompanion && parentAsClass.matches(mockable))

internal fun IrDeclarationWithName.isMockableIn(file: IrFile, mockable: MockablePatterns): Boolean =
    mockable.matches(CallableId(file.packageFqName, name)) ||
        mockable.names.any { hasAnnotation(it) || file.hasAnnotation(it) }

private fun IrClass.matches(mockable: MockablePatterns): Boolean =
    mockable.matches(classIdOrFail) || mockable.names.any { hasAnnotation(it) }

private val IrClass.isHiddenInFile: Boolean
    get() = generateSequence(this) { it.parent as? IrClass }.any { DescriptorVisibilities.isPrivate(it.visibility) }

internal val IrConstructor.isInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && visibility.isInterceptable && !isExpect

internal val IrSimpleFunction.isOpenable: Boolean
    get() = correspondingPropertySymbol == null && !isTailrec && !isClassFinalizer && isOpenableAccessor

internal val IrSimpleFunction.isOpenableAccessor: Boolean
    get() = !isFakeOverride && dispatchReceiverParameter != null && !isInline && !isExpect &&
        !DescriptorVisibilities.isPrivate(visibility)

internal val IrProperty.isOpenable: Boolean
    get() = !isFakeOverride && !isConst && !isExpect && !DescriptorVisibilities.isPrivate(visibility) &&
        getter?.isOpenableAccessor == true && !hasJvmField

internal val IrSimpleFunction.isPrivateInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && correspondingPropertySymbol == null &&
        dispatchReceiverParameter != null && DescriptorVisibilities.isPrivate(visibility) && body != null &&
        !isExpect && exclusion == null && hasExposableSignature

internal val IrSimpleFunction.isInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && !isMethodOfAny() &&
        isInterceptableAccessor

internal val IrSimpleFunction.isInterceptableAccessor: Boolean
    get() = body != null && visibility.isInterceptable && !isExpect && exclusion == null

internal val IrProperty.isInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && visibility.isInterceptable && !isExpect &&
        exclusion == null && getter?.isInterceptableAccessor == true

internal val IrSimpleFunction.isAbstractInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && modality == Modality.ABSTRACT &&
        correspondingPropertySymbol == null && visibility.isInterceptable && !isExpect && exclusion == null

internal val IrProperty.isAbstractInterceptable: Boolean
    get() = origin == IrDeclarationOrigin.DEFINED && !isFakeOverride && modality == Modality.ABSTRACT &&
        visibility.isInterceptable && !isExpect && getter != null && exclusion == null

internal fun IrSimpleFunction.isInheritedInterceptable(lokki: LokkiIrContext): Boolean =
    isFakeOverride && !isMethodOfAny() && isInheritedAccessor(lokki)

internal fun IrSimpleFunction.isInheritedAccessor(lokki: LokkiIrContext): Boolean {
    val inherited = resolveFakeOverride() ?: return false
    return isFakeOverride && visibility.isInterceptable && !isExpect && exclusion == null &&
        inherited.exclusion == null && inherited.modality != Modality.ABSTRACT &&
        (modality != Modality.FINAL || inherited.modality != Modality.FINAL || isOverridable(lokki))
}

internal fun IrProperty.isInheritedInterceptable(lokki: LokkiIrContext): Boolean {
    val getter = getter ?: return false
    val inherited = getter.resolveFakeOverride()?.correspondingPropertySymbol?.owner ?: return false
    return isFakeOverride && visibility.isInterceptable && exclusion == null && inherited.exclusion == null &&
        getter.isInheritedAccessor(lokki)
}

internal val DescriptorVisibility.isInterceptable: Boolean
    get() = !DescriptorVisibilities.isPrivate(this) && this != DescriptorVisibilities.PROTECTED

private val IrProperty.hasJvmField: Boolean
    get() = hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) ||
        backingField?.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) == true

internal val IrSimpleFunction.exclusion: InterceptionExclusion?
    get() = InterceptionExclusion.entries.firstOrNull { it.excludes(this) }

internal val IrProperty.exclusion: InterceptionExclusion?
    get() = InterceptionExclusion.entries.firstOrNull { it.excludes(this) }

private fun InterceptionExclusion.excludes(function: IrSimpleFunction): Boolean =
    when (this) {
        InterceptionExclusion.INLINE -> function.isInline
        InterceptionExclusion.EXTERNAL -> function.isExternal
        InterceptionExclusion.COMPOSABLE -> function.hasAnnotation(COMPOSABLE_ANNOTATION)
        InterceptionExclusion.CONSTANT, InterceptionExclusion.LATEINIT, InterceptionExclusion.JVM_FIELD,
        InterceptionExclusion.TYPE_PARAMETERS -> false
    }

private fun InterceptionExclusion.excludes(property: IrProperty): Boolean {
    val getter = property.getter
    return when (this) {
        InterceptionExclusion.CONSTANT -> property.isConst
        InterceptionExclusion.LATEINIT -> property.isLateinit
        InterceptionExclusion.JVM_FIELD -> property.hasJvmField
        InterceptionExclusion.TYPE_PARAMETERS -> getter?.typeParameters.orEmpty().isNotEmpty()
        InterceptionExclusion.EXTERNAL -> property.isExternal || getter != null && excludes(getter)
        InterceptionExclusion.INLINE, InterceptionExclusion.COMPOSABLE -> getter != null && excludes(getter)
    }
}

private val IrSimpleFunction.hasExposableSignature: Boolean
    get() = returnType.isExposable && nonDispatchParameters.all { it.type.isExposable } &&
        typeParameters.all { parameter -> parameter.superTypes.all { it.isExposable } }

private val IrType.isExposable: Boolean
    get() {
        val owner = classOrNull?.owner
        val hidden = owner != null && generateSequence(owner) { it.parent as? IrClass }.any {
            it.isLocal || DescriptorVisibilities.isPrivate(it.visibility)
        }
        return !hidden && (this as? IrSimpleType)?.arguments.orEmpty().all { it.typeOrNull?.isExposable != false }
    }
