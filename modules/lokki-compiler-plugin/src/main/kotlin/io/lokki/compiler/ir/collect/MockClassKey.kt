package io.lokki.compiler.ir.collect

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.ir.intrinsicKind
import io.lokki.compiler.ir.isVisibleFrom
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableMember
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render

internal data class MockClassKey(val mockedClass: IrClass, val file: IrFile)

internal val IrCall.mockedType: IrType
    get() = typeArguments.single()!!

internal fun IrCall.mockClassKey(file: IrFile): MockClassKey? {
    if (intrinsicKind != IntrinsicKind.MOCK || mockedType.notMockableReason(file) != null) return null
    return MockClassKey(mockedType.classOrNull!!.owner, file)
}

internal fun IrType.notMockableReason(file: IrFile): String? {
    val type = classOrNull?.owner
        ?: return "The mocked type must be known at compile time, but '${render()}' is not a class"
    val name = type.kotlinFqName
    return when {
        type.kind == ClassKind.OBJECT -> "'$name' is an object and cannot be mocked"
        type.kind == ClassKind.ENUM_CLASS || type.kind == ClassKind.ENUM_ENTRY ->
            "'$name' is an enum class and cannot be mocked"
        type.kind == ClassKind.ANNOTATION_CLASS -> "'$name' is an annotation class and cannot be mocked"
        type.modality == Modality.SEALED -> "'$name' is sealed and cannot be mocked"
        type.modality == Modality.FINAL -> "'$name' is final and cannot be mocked"
        (this as IrSimpleType).arguments.isNotEmpty() ->
            "'$name' has type parameters: lokki cannot mock generic types yet"
        type.kind == ClassKind.CLASS && type.constructors.none { it.isVisibleFrom(file) } ->
            "'$name' has no constructor visible to the mock"
        else -> type.hiddenAbstractMembers(file).takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "'$name' has abstract members that the mock cannot implement: ")
    }
}

private fun IrClass.hiddenAbstractMembers(file: IrFile): List<String> =
    declarations.filterIsInstance<IrOverridableMember>()
        .filter { it.modality == Modality.ABSTRACT && !it.isVisibleFrom(file) }
        .map { it.name.asString() }
