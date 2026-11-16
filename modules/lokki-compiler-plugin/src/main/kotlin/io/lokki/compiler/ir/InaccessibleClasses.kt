package io.lokki.compiler.ir

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isNullableArray
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.name.FqName

internal fun IrSimpleFunction.inaccessibleClasses(file: IrFile): List<IrClass> =
    erasedClasses.filterNot { it.isAccessibleFrom(file) }

internal fun IrClass.isAccessibleFrom(file: IrFile): Boolean =
    visibility !in PACKAGE_PRIVATE_CLASSES || packageFqName == file.packageFqName

private val IrSimpleFunction.erasedClasses: List<IrClass>
    get() = (nonDispatchParameters.map { it.type } + returnType).flatMap { it.erasedClasses() }.distinct()

private fun IrType.erasedClasses(): List<IrClass> =
    when (val classifier = classifierOrNull) {
        is IrClassSymbol -> when {
            isArray() || isNullableArray() ->
                (this as IrSimpleType).arguments.single().typeOrNull?.erasedClasses().orEmpty()
            else -> listOf(classifier.owner)
        }
        is IrTypeParameterSymbol -> classifier.owner.superTypes.flatMap { it.erasedClasses() }
        else -> emptyList()
    }

internal val IrClass.packageFqName: FqName
    get() = getPackageFragment().packageFqName

private val PACKAGE_PRIVATE_CLASSES = setOf(DescriptorVisibilities.PRIVATE, JavaDescriptorVisibilities.PACKAGE_VISIBILITY)
