package io.lokki.compiler.ir

import io.lokki.compiler.fir.lokkiEnvironment
import org.jetbrains.kotlin.fir.SessionAndScopeSessionHolder
import org.jetbrains.kotlin.fir.backend.FirMetadataSource
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.lazy.AbstractFir2IrLazyDeclaration
import org.jetbrains.kotlin.fir.lazy.Fir2IrLazyClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val IrDeclaration.hasMetadataSource: Boolean
    get() = (this as? IrMetadataSourceOwner)?.metadata is FirMetadataSource

internal fun IrClass.missingClassesProblem(): String? {
    val holder: SessionAndScopeSessionHolder = this as? Fir2IrLazyClass
        ?: return superTypes.firstNotNullOfOrNull { it.classOrNull?.owner?.missingClassesProblem() }
    return holder.session.lokkiEnvironment.missingClassProblems.getValue(fir.symbol, holder)
}

internal val IrSimpleFunction.isCompiledDefaultFieldAccessor: Boolean?
    get() {
        val property = (correspondingPropertySymbol?.owner as? AbstractFir2IrLazyDeclaration<*>)?.fir as? FirProperty
            ?: return null
        val accessor = (this as? AbstractFir2IrLazyDeclaration<*>)?.fir as? FirPropertyAccessor
        return property.hasBackingField && (accessor == null || accessor is FirDefaultPropertyAccessor)
    }

internal fun IrClass.topLevelClassifierNames(packageName: FqName): Set<Name> =
    (this as? Fir2IrLazyClass)?.session?.symbolProvider?.symbolNamesProvider
        ?.getTopLevelClassifierNamesInPackage(packageName)
        .orEmpty()
