package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.declarations.utils.compilerPluginMetadata
import org.jetbrains.kotlin.fir.lazy.AbstractFir2IrLazyDeclaration
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.resolveFakeOverride

internal val IrClass.effectiveModality: Modality
    get() = if (modality == Modality.FINAL && kind == ClassKind.CLASS && isOpenedInBytecode) Modality.OPEN else modality

internal val IrSimpleFunction.isOverridable: Boolean
    get() = modality != Modality.FINAL ||
        (resolveFakeOverride() ?: this).let { it.correspondingPropertySymbol?.owner ?: it }.isOpenedInBytecode

private val IrDeclaration.isOpenedInBytecode: Boolean
    get() {
        val fir = (this as? AbstractFir2IrLazyDeclaration<*>)?.fir ?: return false
        return InterceptionMark.of(fir.compilerPluginMetadata?.get(BuildConfig.COMPILER_PLUGIN_ID)) ==
            InterceptionMark.CURRENT
    }
