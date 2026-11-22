package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.INTERCEPTION_MARK
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.ir.LokkiIrContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.classIdOrFail
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.JvmStandardClassIds

internal class MockableClasses(lokki: LokkiIrContext, private val mockable: MockablePatterns) {
    private val registrar = lokki.pluginContext.metadataDeclarationRegistrar

    fun open(module: IrModuleFragment) {
        module.files.forEach { file -> file.declarations.forEach(::open) }
    }

    private fun open(declaration: IrDeclaration) {
        if (declaration !is IrClass || declaration.origin != IrDeclarationOrigin.DEFINED) return
        declaration.declarations.forEach(::open)
        if (!declaration.isMockable) return
        declaration.modality = declaration.modality.opened
        for (member in declaration.declarations) {
            when {
                member is IrSimpleFunction && member.isOpenable -> member.open()
                member is IrProperty && member.isOpenable -> {
                    member.open()
                    listOfNotNull(member.getter, member.setter?.takeIf { it.isOpenableAccessor })
                        .forEach { it.modality = it.modality.opened }
                }
            }
        }
        mark(declaration)
    }

    private val IrClass.isMockable: Boolean
        get() = kind == ClassKind.CLASS && !isValue && !isExpect && modality != Modality.SEALED &&
            !hasAnnotation(JvmStandardClassIds.JVM_RECORD_ANNOTATION_CLASS_ID) &&
            (mockable.matches(classIdOrFail) || mockable.names.any { hasAnnotation(it) })

    private val IrSimpleFunction.isOpenable: Boolean
        get() = correspondingPropertySymbol == null && !isTailrec && isOpenableAccessor

    private val IrSimpleFunction.isOpenableAccessor: Boolean
        get() = !isFakeOverride && dispatchReceiverParameter != null && !isInline && !isExpect &&
            !DescriptorVisibilities.isPrivate(visibility)

    private val IrProperty.isOpenable: Boolean
        get() = !isFakeOverride && !isConst && !isExpect && !DescriptorVisibilities.isPrivate(visibility) &&
            getter?.isOpenableAccessor == true && !hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) &&
            backingField?.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) != true

    private fun <T> T.open() where T : IrOverridableDeclaration<*>, T : IrMetadataSourceOwner {
        if (modality != Modality.FINAL) return
        modality = Modality.OPEN
        mark(this)
    }

    private fun <T> mark(declaration: T) where T : IrDeclaration, T : IrMetadataSourceOwner {
        if (declaration.metadata !is FirMetadataSource) return
        registrar.addCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID, INTERCEPTION_MARK)
    }

    private val Modality.opened: Modality
        get() = if (this == Modality.FINAL) Modality.OPEN else this
}
