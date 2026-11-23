package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.FINALIZE
import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.declarations.utils.compilerPluginMetadata
import org.jetbrains.kotlin.fir.lazy.AbstractFir2IrLazyDeclaration
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail

internal val IrClass.effectiveModality: Modality
    get() = if (modality == Modality.FINAL && kind == ClassKind.CLASS && isOpenedInBytecode) Modality.OPEN else modality

internal val IrSimpleFunction.isOverridable: Boolean
    get() = modality != Modality.FINAL ||
        (resolveFakeOverride() ?: this).let { it.correspondingPropertySymbol?.owner ?: it }.isOpenedInBytecode

internal val IrSimpleFunction.isClassFinalizer: Boolean
    get() {
        if (name.asString() != FINALIZE || nonDispatchParameters.isNotEmpty() || !returnType.isUnit()) return false
        val declared = resolveFakeOverrideMaybeAbstractOrFail()
        return declared.modality != Modality.ABSTRACT && !declared.parentAsClass.isInterface
    }

private val IrDeclaration.isOpenedInBytecode: Boolean
    get() {
        val fir = (this as? AbstractFir2IrLazyDeclaration<*>)?.fir ?: return false
        return InterceptionMark.of(fir.compilerPluginMetadata?.get(BuildConfig.COMPILER_PLUGIN_ID)) ==
            InterceptionMark.CURRENT
    }
