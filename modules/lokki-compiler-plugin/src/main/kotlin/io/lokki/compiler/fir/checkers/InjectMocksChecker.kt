package io.lokki.compiler.fir.checkers

import io.lokki.compiler.InjectionConstructor
import io.lokki.compiler.InjectionParameter
import io.lokki.compiler.InjectionProblem
import io.lokki.compiler.InjectionProperty
import io.lokki.compiler.InjectionSource
import io.lokki.compiler.InjectionTarget
import io.lokki.compiler.InjectionTypes
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.fieldKind
import io.lokki.compiler.fir.isAccessibleOutsideHierarchy
import io.lokki.compiler.fir.isVisibleToMock
import io.lokki.compiler.plan
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.declaredProperties
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLateInit
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.scopes.CallableCopyTypeCalculator
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.processAllProperties
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.isMarkedOrFlexiblyNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.AbstractTypeChecker

internal object InjectMocksChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val session = context.session
        val targets = declaration.symbol.declaredProperties(session).mapNotNull { property ->
            val kinds = property.resolvedAnnotationsWithArguments.associateWith { it.fieldKind(session) }
                .filterValues { it != null }
            val inject = kinds.entries.find { it.value == IntrinsicKind.INJECT_MOCKS }?.key ?: return@mapNotNull null
            val valid = property.isVar && property.correspondingValueParameterFromPrimaryConstructor == null &&
                kinds.values.all { it == IntrinsicKind.INJECT_MOCKS || it == IntrinsicKind.SPY }
            if (valid) property to inject else null
        }
        if (targets.isEmpty()) return
        val types = FirInjectionTypes(session)
        val sources = ArrayList<InjectionSource<ConeKotlinType>>()
        declaration.symbol.unsubstitutedScope().processAllProperties { symbol ->
            if (symbol !is FirPropertySymbol || symbol.resolvedReceiverType != null) return@processAllProperties
            val own = symbol.unwrapFakeOverrides().callableId?.classId == declaration.symbol.classId
            if (!own && !symbol.isVisibleToMock()) return@processAllProperties
            val target = targets.indexOfFirst { (property, _) -> property == symbol }.takeIf { it >= 0 }
            sources += InjectionSource(symbol.name, symbol.resolvedReturnType, target)
        }
        val injected = targets.map { (property, annotation) -> injectedType(property) to annotation }
        val plan = types.plan(
            sources,
            targets.zip(injected) { (property, annotation), (type, _) -> target(property.name, type, annotation) },
        )
        for ((index, problem) in plan.problems) {
            val (type, annotation) = injected[index]
            val factory = when (problem.kind) {
                InjectionProblem.Kind.UNRESOLVED -> LokkiDiagnostics.LOKKI_UNRESOLVED_INJECTION
                InjectionProblem.Kind.AMBIGUOUS -> LokkiDiagnostics.LOKKI_AMBIGUOUS_INJECTION
                InjectionProblem.Kind.UNSUPPORTED -> LokkiDiagnostics.LOKKI_UNSUPPORTED_INJECTION
            }
            val name = type.toRegularClassSymbol(session)?.displayName ?: type.renderReadable()
            reporter.reportOn(annotation.source, factory, name, problem.message)
        }
    }
}

private class FirInjectionTypes(private val session: FirSession) : InjectionTypes<ConeKotlinType> {
    override fun isAssignable(value: ConeKotlinType, target: ConeKotlinType): Boolean =
        AbstractTypeChecker.isSubtypeOf(session.typeContext, value.notNull(), target.notNull())

    override fun listElement(type: ConeKotlinType): ConeKotlinType? =
        (type.lowerBoundIfFlexible() as? ConeClassLikeType)
            ?.takeIf { it.lookupTag.classId == StandardClassIds.List }
            ?.typeArguments?.singleOrNull()?.type

    private fun ConeKotlinType.notNull(): ConeKotlinType =
        fullyExpandedType(session).lowerBoundIfFlexible().withNullability(false, session.typeContext)
}

context(context: CheckerContext)
private fun injectedType(property: FirPropertySymbol): ConeKotlinType =
    property.resolvedReturnType.fullyExpandedType().lowerBoundIfFlexible()
        .withNullability(false, context.session.typeContext)

context(context: CheckerContext)
private fun target(
    name: Name,
    type: ConeKotlinType,
    annotation: FirAnnotation,
): InjectionTarget<ConeKotlinType, FirConstructorSymbol, FirPropertySymbol> {
    val lookupType = annotation.findArgumentByName(LOOKUP_TYPE, returnFirstWhenNotFound = false)
        ?.extractEnumValueArgumentInfo()?.enumEntryName?.asString()
    val byName = lookupType != BY_TYPE
    val byType = lookupType != BY_NAME
    val symbol = (type as? ConeClassLikeType)?.toRegularClassSymbol(context.session)
    val problem = when {
        type is ConeTypeParameterType -> "it is a type parameter"
        symbol == null -> "it is not a class"
        symbol.classKind == ClassKind.INTERFACE -> "it is an interface"
        symbol.classKind != ClassKind.CLASS -> "it is not a class"
        symbol.modality == Modality.ABSTRACT || symbol.modality == Modality.SEALED -> "it is an abstract class"
        symbol.isInner -> "it is an inner class"
        else -> null
    }
    if (problem != null) return InjectionTarget(name, byName, byType, emptyList(), emptyList(), problem)
    symbol!!
    type as ConeClassLikeType
    val substitutor = substitutorByMap(
        symbol.typeParameterSymbols.zip(type.typeArguments).mapNotNull { (parameter, argument) ->
            argument.type?.let { parameter to it }
        }.toMap(),
        context.session,
    )
    val constructors = symbol.unsubstitutedScope().getDeclaredConstructors()
        .filter { it.isAccessibleOutsideHierarchy() }
        .map { constructor ->
            val parameters = constructor.valueParameterSymbols.map { parameter ->
                InjectionParameter(
                    parameter.name,
                    substitutor.substituteOrSelf(parameter.resolvedReturnType),
                    parameter.hasDefaultValue || parameter.isVararg,
                )
            }
            val signature = parameters.joinToString(prefix = "${symbol.name}(", postfix = ")") {
                "${it.name}: ${it.type.renderReadable()}"
            }
            InjectionConstructor(constructor, signature, parameters)
        }
    val properties = ArrayList<InjectionProperty<ConeKotlinType, FirPropertySymbol>>()
    val scope = type.scope(
        context.session,
        context.scopeSession,
        CallableCopyTypeCalculator.CalculateDeferredForceLazyResolution,
        FirResolvePhase.STATUS,
    )
    scope?.processAllProperties { property ->
        if (property !is FirPropertySymbol || !property.isVar || property.resolvedReceiverType != null) {
            return@processAllProperties
        }
        if (!property.isLateInit && !property.resolvedReturnType.isMarkedOrFlexiblyNullable) return@processAllProperties
        val own = property.unwrapFakeOverrides().callableId?.classId == symbol.classId
        if (property.visibility == Visibilities.Private && !own) return@processAllProperties
        val setter = property.setterSymbol?.visibility ?: property.visibility
        val settable = property.isAccessibleOutsideHierarchy() && property.isAccessibleOutsideHierarchy(setter)
        properties += InjectionProperty(property, property.name, property.resolvedReturnType, settable)
    }
    return InjectionTarget(name, byName, byType, constructors, properties)
}

private val LOOKUP_TYPE = Name.identifier("lookupType")
private const val BY_NAME = "BY_NAME"
private const val BY_TYPE = "BY_TYPE"
