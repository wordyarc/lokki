package io.lokki.compiler.ir

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.lazy.IrLazyDeclarationBase
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isNullableArray
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

internal val IrSimpleFunction.missingClasses: List<IrClass>
    get() = (nonDispatchParameters.map { it.type } + returnType).flatMap { it.erasedMissingClasses() }.distinct()

private fun IrType.erasedMissingClasses(): List<IrClass> =
    when (val classifier = classifierOrNull) {
        is IrClassSymbol -> when {
            classifier.owner.isMissing -> listOf(classifier.owner)
            isArray() || isNullableArray() ->
                (this as IrSimpleType).arguments.single().typeOrNull?.erasedMissingClasses().orEmpty()
            else -> emptyList()
        }
        is IrTypeParameterSymbol -> classifier.owner.superTypes.flatMap { it.erasedMissingClasses() }
        else -> emptyList()
    }

private val IrClass.isMissing: Boolean
    get() = origin == IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB && this !is IrLazyDeclarationBase
