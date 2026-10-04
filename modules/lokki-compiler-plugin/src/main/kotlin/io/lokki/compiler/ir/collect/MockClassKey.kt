package io.lokki.compiler.ir.collect

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render

internal data class MockClassKey(val mockedClass: IrClass, val file: IrFile)

internal val IrCall.mockedType: IrType
    get() = typeArguments.single()!!

internal fun IrCall.mockClassKey(file: IrFile): MockClassKey? {
    if (intrinsicKind != IntrinsicKind.MOCK || mockedType.notMockableReason != null) return null
    return MockClassKey(mockedType.classOrNull!!.owner, file)
}

internal val IrType.notMockableReason: String?
    get() {
        val type = classOrNull?.owner
            ?: return "The mocked type must be known at compile time, but '${render()}' is not a class"
        return when {
            !type.isInterface -> "'${type.kotlinFqName}' is not an interface: lokki cannot mock classes yet"
            type.modality == Modality.SEALED -> "'${type.kotlinFqName}' is sealed and cannot be mocked"
            type.typeParameters.isNotEmpty() ->
                "'${type.kotlinFqName}' has type parameters: lokki cannot mock generic types yet"
            else -> null
        }
    }
