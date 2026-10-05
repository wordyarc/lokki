package io.lokki.compiler.fir.checkers

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isFinalType
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaredMemberScope
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal object MockChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() ?: return
        if (function.intrinsicKind(context.session) != IntrinsicKind.MOCK) return
        val argument = expression.typeArguments.singleOrNull() ?: return
        val source = argument.source?.takeIf { it.kind is KtRealSourceElementKind } ?: expression.source
        val type = argument.toConeTypeProjection().type?.lowerBoundIfFlexible()?.fullyExpandedType() ?: return
        if (type is ConeTypeParameterType) {
            reporter.reportOn(
                source,
                LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
                function.name.asString(),
                type.lookupTag.name.asString(),
            )
            return
        }
        val mocked = type.takeUnless { it.isNothing }?.toRegularClassSymbol(context.session) ?: return
        val reason = mocked.notMockableReason()
        if (reason != null) {
            reporter.reportOn(source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, reason)
            return
        }
        mocked.restriction(context.session)?.let { restriction ->
            reporter.reportOn(source, LokkiDiagnostics.LOKKI_RESTRICTED_TYPE, mocked.displayName, restriction)
        }
        val finalMembers = mocked.finalMembers()
        if (finalMembers.isNotEmpty()) {
            reporter.reportOn(
                source,
                LokkiDiagnostics.LOKKI_FINAL_MEMBERS_NOT_MOCKED,
                mocked.displayName,
                finalMembers.joinToString { "'$it'" },
            )
        }
    }
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.notMockableReason(): String? =
    when {
        classKind == ClassKind.OBJECT -> "it is an object"
        classKind == ClassKind.ENUM_CLASS || classKind == ClassKind.ENUM_ENTRY -> "it is an enum class"
        classKind == ClassKind.ANNOTATION_CLASS -> "it is an annotation class"
        modality == Modality.SEALED -> "it is sealed"
        isFinalType -> "it is a final class. Extract an interface or make the class and its members open"
        classKind != ClassKind.CLASS -> null
        declaredMemberScope().getDeclaredConstructors().none { it.isVisibleToMock() } ->
            "none of its constructors is visible from this file"
        else -> hiddenAbstractMembers().takeIf { it.isNotEmpty() }?.let { members ->
            "a mock cannot implement its abstract members that are not visible from this file: " +
                members.joinToString { "'$it'" }
        }
    }

context(context: CheckerContext)
private fun FirRegularClassSymbol.hiddenAbstractMembers(): List<String> {
    val members = sortedSetOf<String>()
    unsubstitutedScope().processAllCallables { member ->
        if (member.isAbstract && !member.isVisibleToMock()) members += member.name.asString()
    }
    return members.toList()
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.finalMembers(): List<String> {
    if (classKind != ClassKind.CLASS) return emptyList()
    val members = sortedSetOf<String>()
    unsubstitutedScope().processAllCallables { member ->
        val declaration = member.unwrapFakeOverrides()
        if (member.isFinal && member.visibility != Visibilities.Private &&
            context.session.moduleData.canSeeInternalsOf(declaration.moduleData)
        ) {
            members += member.name.asString()
        }
    }
    return members.toList()
}

context(context: CheckerContext)
private fun FirCallableSymbol<*>.isVisibleToMock(): Boolean {
    val declaration = unwrapFakeOverrides()
    return when (val visibility = visibility) {
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

private val CheckerContext.filePackage: FqName?
    get() = containingDeclarations.firstNotNullOfOrNull { declaration ->
        when (declaration) {
            is FirClassLikeSymbol<*> -> declaration.classId.packageFqName
            is FirCallableSymbol<*> -> declaration.callableId?.packageName
            else -> null
        }
    }

private fun FirRegularClassSymbol.restriction(session: FirSession): String? {
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, session)
        .mapNotNull { it.toRegularClassSymbol(session) }
    for (type in listOf(this) + supertypes) {
        val annotation = type.resolvedAnnotationsWithArguments
            .find { it.toAnnotationClassId(session)?.shortClassName == DO_NOT_MOCK }
        if (annotation != null) {
            val advice = annotation.getStringArgument(VALUE)?.trimEnd('.')
            return "'${type.displayName}' is annotated with '$DO_NOT_MOCK'" + advice?.let { ": $it" }.orEmpty()
        }
        if (type.classId in RESTRICTED_TYPES) return "use a real '${type.displayName}' instead"
    }
    return null
}

private val DO_NOT_MOCK = Name.identifier("DoNotMock")
private val VALUE = Name.identifier("value")
private val RESTRICTED_TYPES = setOf(
    StandardClassIds.Collection,
    ClassId.fromString("java/util/HashMap"),
    ClassId.fromString("java/io/File"),
    ClassId.fromString("java/nio/file/Path"),
)
