package io.lokki.compiler.ir

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.moduleDescriptor
import org.jetbrains.kotlin.ir.overrides.FakeOverrideBuilderStrategy
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.StandardClassIds

internal fun IrDeclarationWithVisibility.isVisibleFrom(file: IrFile): Boolean {
    val fragment = getPackageFragment()
    return when {
        DescriptorVisibilities.isPrivate(visibility) -> false
        visibility == DescriptorVisibilities.INTERNAL ->
            file.moduleDescriptor.shouldSeeInternalsOf(fragment.moduleDescriptor) ||
                hasAnnotation(StandardClassIds.Annotations.PublishedApi)
        else -> visibility.visibleFromPackage(file.packageFqName, fragment.packageFqName)
    }
}

internal object FriendAwareFakeOverrides : FakeOverrideBuilderStrategy.BindToPrivateSymbols() {
    override fun postProcessGeneratedFakeOverride(fakeOverride: IrOverridableDeclaration<*>, clazz: IrClass) {}

    override fun shouldSeeInternals(thisModule: ModuleDescriptor, memberModule: ModuleDescriptor): Boolean =
        thisModule.shouldSeeInternalsOf(memberModule)
}
