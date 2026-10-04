package io.lokki.compiler.ir.collect

import io.lokki.compiler.ir.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.isInterface

internal data class MockClassKey(val mockedClass: IrClass, val file: IrFile)

internal fun IrCall.mockClassKey(file: IrFile): MockClassKey? {
    if (intrinsicKind != IntrinsicKind.MOCK) return null
    val mockedClass = typeArguments.single()?.classOrNull?.owner?.takeIf { it.isMockable } ?: return null
    return MockClassKey(mockedClass, file)
}

private val IrClass.isMockable: Boolean
    get() = isInterface && modality != Modality.SEALED && typeParameters.isEmpty()
