package io.lokki.compiler.ir.generate

import io.lokki.compiler.BuildConfig
import io.lokki.compiler.INTERCEPTION_MARK
import io.lokki.compiler.MockablePatterns
import io.lokki.compiler.ir.LokkiIrContext
import io.lokki.compiler.ir.hasMetadataSource
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

internal class MockableClasses(lokki: LokkiIrContext, private val mockable: MockablePatterns) {
    private val registrar = lokki.pluginContext.metadataDeclarationRegistrar

    fun open(classes: List<IrClass>) {
        classes.filter { it.isMockableBy(mockable) }.forEach(::open)
    }

    private fun open(declaration: IrClass) {
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

    private fun <T> T.open() where T : IrOverridableDeclaration<*>, T : IrMetadataSourceOwner {
        if (modality != Modality.FINAL) return
        modality = Modality.OPEN
        mark(this)
    }

    private fun <T> mark(declaration: T) where T : IrDeclaration, T : IrMetadataSourceOwner {
        if (!declaration.hasMetadataSource) return
        registrar.addCustomMetadataExtension(declaration, BuildConfig.COMPILER_PLUGIN_ID, INTERCEPTION_MARK)
    }

    private val Modality.opened: Modality
        get() = if (this == Modality.FINAL) Modality.OPEN else this
}
