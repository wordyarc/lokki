package io.lokki.compiler.ir.collect

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal data class MockClassKey(val mockedClass: IrClass, val file: IrFile)

internal fun IrCall.mockClassKey(file: IrFile): MockClassKey? {
    if (intrinsicKind != MOCK) return null
    val mockedClass = typeArguments.single()?.classOrNull?.owner?.takeIf { it.isMockable } ?: return null
    return MockClassKey(mockedClass, file)
}

private val IrClass.isMockable: Boolean
    get() = isInterface && modality != Modality.SEALED && typeParameters.isEmpty()

private val IrCall.intrinsicKind: Name?
    get() = (symbol.owner.getAnnotation(LOKKI_INTRINSIC)?.arguments?.single() as? IrGetEnumValue)?.symbol?.owner?.name

private val LOKKI_INTRINSIC = FqName("io.lokki.internal.LokkiIntrinsic")
private val MOCK = Name.identifier("MOCK")
