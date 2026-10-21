package io.lokki.compiler.fir

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.java.JavaVisibilities
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isVisibleToMock(visibility: Visibility = this.visibility): Boolean {
    val declaration = unwrapFakeOverrides()
    return when (visibility) {
        Visibilities.Private, Visibilities.PrivateToThis, Visibilities.Local -> false
        Visibilities.Internal ->
            context.session.moduleData.canSeeInternalsOf(declaration.moduleData) ||
                declaration.hasAnnotation(StandardClassIds.Annotations.PublishedApi, context.session)
        else -> {
            val filePackage = context.filePackage ?: return true
            val declarationPackage = declaration.callableId?.packageName ?: return true
            visibility.visibleFromPackage(filePackage, declarationPackage)
        }
    }
}

context(context: CheckerContext)
internal fun FirCallableSymbol<*>.isAccessibleOutsideHierarchy(visibility: Visibility = this.visibility): Boolean =
    when (visibility) {
        Visibilities.Protected -> false
        JavaVisibilities.ProtectedAndPackage, JavaVisibilities.ProtectedStaticVisibility ->
            isVisibleToMock(JavaVisibilities.PackageVisibility)
        else -> isVisibleToMock(visibility)
    }

internal val CheckerContext.filePackage: FqName?
    get() = containingDeclarations.firstNotNullOfOrNull { declaration ->
        when (declaration) {
            is FirClassLikeSymbol<*> -> declaration.classId.packageFqName
            is FirCallableSymbol<*> -> declaration.callableId?.packageName
            else -> null
        }
    }
