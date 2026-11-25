package io.lokki.compiler.ir

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.FINALIZE
import io.lokki.compiler.InterceptionMark
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.resolveFakeOverrideMaybeAbstractOrFail

internal fun IrClass.effectiveModality(lokki: LokkiIrContext): Modality =
    if (modality == Modality.FINAL && kind == ClassKind.CLASS && isOpenedInBytecode(lokki)) Modality.OPEN else modality

internal fun IrSimpleFunction.isOverridable(lokki: LokkiIrContext): Boolean =
    modality != Modality.FINAL ||
        (resolveFakeOverride() ?: this).let { it.correspondingPropertySymbol?.owner ?: it }.isOpenedInBytecode(lokki)

internal val IrSimpleFunction.isPrivateHook: Boolean
    get() = BuildConfig.PRIVATE_HOOK_INFIX in name.asString()

internal val IrSimpleFunction.isClassFinalizer: Boolean
    get() {
        if (name.asString() != FINALIZE || nonDispatchParameters.isNotEmpty() || !returnType.isUnit()) return false
        val declared = resolveFakeOverrideMaybeAbstractOrFail()
        return declared.modality != Modality.ABSTRACT && !declared.parentAsClass.isInterface
    }

private fun IrDeclaration.isOpenedInBytecode(lokki: LokkiIrContext): Boolean =
    lokki.mark(this) == InterceptionMark.CURRENT
