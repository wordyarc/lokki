package io.lokki.compiler.ir

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrOverridableDeclaration
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.moduleDescriptor
import org.jetbrains.kotlin.ir.overrides.FakeOverrideBuilderStrategy
import org.jetbrains.kotlin.ir.overrides.IrFakeOverrideBuilder
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.load.java.JavaDescriptorVisibilities
import org.jetbrains.kotlin.name.StandardClassIds

internal fun IrDeclarationWithVisibility.isVisibleFrom(
    file: IrFile,
    visibility: DescriptorVisibility = this.visibility,
): Boolean {
    val fragment = getPackageFragment()
    return when {
        DescriptorVisibilities.isPrivate(visibility) -> false
        visibility == DescriptorVisibilities.INTERNAL ->
            file.moduleDescriptor.shouldSeeInternalsOf(fragment.moduleDescriptor) ||
                hasAnnotation(StandardClassIds.Annotations.PublishedApi)
        else -> visibility.visibleFromPackage(file.packageFqName, fragment.packageFqName)
    }
}

internal fun IrDeclarationWithVisibility.isAccessibleOutsideHierarchy(
    file: IrFile,
    visibility: DescriptorVisibility = this.visibility,
): Boolean =
    when (visibility) {
        DescriptorVisibilities.PROTECTED -> false
        JavaDescriptorVisibilities.PROTECTED_AND_PACKAGE, JavaDescriptorVisibilities.PROTECTED_STATIC_VISIBILITY ->
            isVisibleFrom(file, JavaDescriptorVisibilities.PACKAGE_VISIBILITY)
        else -> isVisibleFrom(file, visibility)
    }

internal fun IrClass.addFriendAwareFakeOverrides(typeSystem: IrTypeSystemContext) {
    IrFakeOverrideBuilder(typeSystem, FriendAwareFakeOverrides, emptyList())
        .buildFakeOverridesForClassUsingOverriddenSymbols(this, emptyMap(), compatibilityMode = false)
        .onEach { if (it is IrProperty && it.getter == null) it.backingField = null }
        .forEach(::addChild)
}

private object FriendAwareFakeOverrides : FakeOverrideBuilderStrategy.BindToPrivateSymbols() {
    override fun postProcessGeneratedFakeOverride(fakeOverride: IrOverridableDeclaration<*>, clazz: IrClass) {}

    override fun shouldSeeInternals(thisModule: ModuleDescriptor, memberModule: ModuleDescriptor): Boolean =
        thisModule.shouldSeeInternalsOf(memberModule)
}
