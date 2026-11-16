package io.lokki.compiler.fir.checkers

import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.NOT_CLASS_PROBLEM
import io.lokki.compiler.TYPE_PARAMETER_PROBLEM
import io.lokki.compiler.declarationKindProblem
import io.lokki.compiler.fir.classifier
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.fieldKind
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isFromFriendKotlinModule
import io.lokki.compiler.linked
import io.lokki.compiler.memberWithClasses
import io.lokki.compiler.missingMemberClassesProblem
import io.lokki.compiler.missingSupertypesProblem
import io.lokki.compiler.notInterfaceProblem
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.resolve.toTypeParameterSymbol
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.contains
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkMock(call: FirFunctionCall, function: FirCallableSymbol<*>, kind: IntrinsicKind) {
    val instance = if (kind == IntrinsicKind.SPY) spiedInstance(call, function) else null
    val argument = call.typeArguments.singleOrNull() ?: return
    val source = when {
        instance != null -> instance.source
        else -> argument.source?.takeIf { it.kind is KtRealSourceElementKind } ?: call.source
    }
    val type = instance?.constructedType()
        ?: argument.toConeTypeProjection().type?.lowerBoundIfFlexible()?.fullyExpandedType()
        ?: return
    val mocked = MockedType(kind, type, source, "The type argument", function.name.asString())
    mocked.check { moreInterfaces(call, function) }
}

internal object MockPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) = linked { inspect(declaration) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(declaration: FirProperty) {
        val session = context.session
        val annotations = declaration.annotations.filter { it.fieldKind(session) != null }
        val inject = annotations.find { it.fieldKind(session) == IntrinsicKind.INJECT_MOCKS }
        val annotation = annotations.find { it !== inject } ?: inject ?: return
        checkRuntimeVersion(annotation.source)
        val kind = annotation.fieldKind(session)!!
        val name = annotation.toAnnotationClassLikeSymbol(session)!!.name.asString()
        val parameter = declaration.correspondingValueParameterFromPrimaryConstructor
        if (parameter != null) {
            if (parameter.resolvedAnnotationsWithArguments.none { it.fieldKind(session) != null }) {
                reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_UNSUPPORTED_MOCK_PARAMETER, name)
            }
            return
        }
        if (declaration.isLocal) return
        val declaredType = declaration.returnTypeRef.coneType.lowerBoundIfFlexible().fullyExpandedType()
        if (inject != null && kind != IntrinsicKind.INJECT_MOCKS && kind != IntrinsicKind.SPY) {
            reporter.reportOn(
                inject.source,
                LokkiDiagnostics.LOKKI_UNSUPPORTED_INJECTION,
                declaredType.toRegularClassSymbol(session)?.displayName ?: declaredType.renderReadable(),
                "a property cannot have both '@InjectMocks' and '@$name'",
            )
            return
        }
        if (kind == IntrinsicKind.INJECT_MOCKS) {
            if (!declaration.isVar) {
                reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_MOCK_PROPERTY_NOT_MUTABLE, name, "")
            }
            return
        }
        val constructed = if (kind == IntrinsicKind.SPY) declaration.initializer?.constructedType() else null
        val mocked = MockedType(
            if (kind == IntrinsicKind.SPY) kind else IntrinsicKind.MOCK,
            constructed ?: declaredType,
            annotation.source,
            "The mocked type",
            "@$name",
        )
        if (!mocked.check { additionalInterface(declaration) }) return
        if (!declaration.isVar && (inject != null || constructed == null)) {
            val hint = if (kind == IntrinsicKind.SPY && inject == null) SPY_VAL_HINT else ""
            reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_MOCK_PROPERTY_NOT_MUTABLE, name, hint)
        }
    }
}

internal object MockParameterChecker : FirValueParameterChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirValueParameter) = linked { inspect(declaration) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(declaration: FirValueParameter) {
        val session = context.session
        val annotation = declaration.annotations.find { it.fieldKind(session) != null } ?: return
        val name = annotation.toAnnotationClassLikeSymbol(session)!!.name.asString()
        reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_UNSUPPORTED_MOCK_PARAMETER, name)
    }
}

private class MockedType(
    val kind: IntrinsicKind,
    val type: ConeKotlinType,
    val source: KtSourceElement?,
    val subject: String,
    val dsl: String,
)

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun MockedType.check(interfaces: () -> Boolean): Boolean {
    if (type is ConeTypeParameterType) {
        reporter.reportOn(
            source,
            LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
            subject,
            dsl,
            "'${type.classifier.name}' is a type parameter",
        )
        return false
    }
    val mocked = type.takeUnless { it.isNothing }?.toRegularClassSymbol(context.session) ?: return false
    val problem = mocked.problem()
    if (problem != null) {
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, problem)
        return false
    }
    if (!interfaces()) return false
    if (kind == IntrinsicKind.SPY) return true
    mocked.restriction(context.session)?.let { restriction ->
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_RESTRICTED_TYPE, mocked.displayName, restriction)
    }
    val finalMembers = mocked.finalMembers()
    if (finalMembers.isNotEmpty()) {
        val advice = if (mocked.isFromFriendKotlinModule) {
            "Make them open or add their classes to lokki { mockable(...) }"
        } else {
            "Make them open"
        }
        reporter.reportOn(
            source,
            LokkiDiagnostics.LOKKI_FINAL_MEMBERS_NOT_MOCKED,
            mocked.displayName,
            finalMembers.joinToString { "'$it'" },
            advice,
        )
    }
    return true
}

context(context: CheckerContext)
internal fun ConeKotlinType.childMockProblem(): Pair<String, String>? {
    val type = when (val expanded = fullyExpandedType().lowerBoundIfFlexible()) {
        is ConeDefinitelyNotNullType -> expanded.original
        else -> expanded
    }
    if (type is ConeTypeParameterType) return type.classifier.name.asString() to TYPE_PARAMETER_PROBLEM
    val mocked = type.toRegularClassSymbol(context.session) ?: return type.renderReadable() to NOT_CLASS_PROBLEM
    return mocked.problem()?.let { mocked.displayName to it }
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.problem(): String? =
    declarationKindProblem(classKind, modality, suggestsMockable = isFromFriendKotlinModule && !isInlineOrValue)
        ?: missingSupertypes().takeIf { it.isNotEmpty() }?.let(::missingSupertypesProblem)
        ?: membersWithMissingClasses().takeIf { it.isNotEmpty() }?.let(::missingMemberClassesProblem)

context(context: CheckerContext)
private fun FirRegularClassSymbol.missingSupertypes(): List<String> =
    lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .filter { it.lookupTag.toSymbol(context.session) == null }
        .map { it.lookupTag.classId.asFqNameString() }
        .distinct()

context(context: CheckerContext)
private fun FirRegularClassSymbol.membersWithMissingClasses(): Set<String> {
    val members = sortedSetOf<String>()
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .mapNotNull { it.toRegularClassSymbol(context.session) }
    for (type in listOf(this) + supertypes) {
        type.unsubstitutedScope().processAllCallables { member ->
            val missing = member.erasedClasses().filter { it.lookupTag.toSymbol(context.session) == null }
            if (missing.isNotEmpty()) {
                val classes = missing.map { it.lookupTag.classId.asFqNameString() }
                members += memberWithClasses(member.name.asString(), classes)
            }
        }
    }
    return members
}

context(context: CheckerContext)
private fun FirCallableSymbol<*>.erasedClasses(): List<ConeClassLikeType> =
    signatureTypes().flatMap { it.erasedClasses() }.distinctBy { it.lookupTag }

private fun FirCallableSymbol<*>.signatureTypes(): List<ConeKotlinType> =
    listOfNotNull(resolvedReturnType, resolvedReceiverType) +
        contextParameterSymbols.map { it.resolvedReturnType } +
        (this as? FirFunctionSymbol<*>)?.valueParameterSymbols.orEmpty().map { it.resolvedReturnType }

context(context: CheckerContext)
private fun ConeKotlinType.erasedClasses(): List<ConeClassLikeType> =
    when (val type = lowerBoundIfFlexible()) {
        is ConeClassLikeType -> when {
            type.lookupTag.toSymbol(context.session) != null && type.isNonPrimitiveArray ->
                type.typeArguments.single().type?.erasedClasses().orEmpty()
            else -> listOf(type)
        }
        is ConeTypeParameterType ->
            type.toTypeParameterSymbol(context.session)?.resolvedBounds.orEmpty().flatMap { it.coneType.erasedClasses() }
        is ConeDefinitelyNotNullType -> type.original.erasedClasses()
        else -> emptyList()
    }

private fun spiedInstance(call: FirFunctionCall, function: FirCallableSymbol<*>): FirExpression? {
    val parameter = (function as? FirFunctionSymbol<*>)?.valueParameterSymbols?.find { it.name == SPIED_INSTANCE }
        ?: return null
    return call.resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key
}

context(context: CheckerContext)
private fun FirExpression.constructedType(): ConeKotlinType? {
    val call = unwrapArgument() as? FirFunctionCall ?: return null
    if (call.calleeReference.toResolvedCallableSymbol() !is FirConstructorSymbol) return null
    val type = call.resolvedType.fullyExpandedType()
    val constructed = type.toRegularClassSymbol(context.session) ?: return null
    return type.takeIf {
        constructed.classKind == ClassKind.CLASS && constructed.modality != Modality.FINAL &&
            constructed.modality != Modality.SEALED
    }
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

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun moreInterfaces(call: FirFunctionCall, function: FirCallableSymbol<*>): Boolean {
    val parameter = (function as? FirFunctionSymbol<*>)?.valueParameterSymbols?.find { it.name == MORE_INTERFACES }
        ?: return true
    val argument = call.resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key ?: return true
    val dsl = function.name.asString()
    return interfaces(argument.interfaceArguments(), "An interface in 'moreInterfaces'", dsl, "'moreInterfaces'")
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun additionalInterface(property: FirProperty): Boolean {
    val annotation = property.annotations
        .find { it.toAnnotationClassLikeSymbol(context.session)?.intrinsicKind(context.session) == ADDITIONAL_INTERFACE }
        ?: return true
    val type = annotation.findArgumentByName(TYPE) ?: return true
    return interfaces(listOf(type), "The interface", "@AdditionalInterface", "'@AdditionalInterface'")
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun interfaces(elements: List<FirExpression>, subject: String, dsl: String, owner: String): Boolean {
    var valid = true
    for (element in elements) {
        val target = (element as? FirGetClassCall)?.getTargetType()?.fullyExpandedType()
        val notConcrete = when (target) {
            null -> "it is not a class literal such as 'Closeable::class'"
            is ConeTypeParameterType -> "'${target.classifier.name}' is a type parameter"
            else -> null
        }
        if (notConcrete != null) {
            reporter.reportOn(element.source, LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE, subject, dsl, notConcrete)
            valid = false
            continue
        }
        val symbol = target?.toRegularClassSymbol(context.session) ?: continue
        val reason = if (symbol.classKind != ClassKind.INTERFACE) notInterfaceProblem(owner) else symbol.problem()
        if (reason != null) {
            reporter.reportOn(element.source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, symbol.displayName, reason)
            valid = false
        }
    }
    return valid
}

private fun FirExpression.interfaceArguments(): List<FirExpression> =
    when (val expression = unwrapArgument()) {
        is FirVarargArgumentsExpression -> expression.arguments.flatMap { it.interfaceArguments() }
        is FirFunctionCall ->
            if (expression.calleeReference.toResolvedCallableSymbol()?.callableId == ARRAY_OF) {
                expression.arguments.flatMap { it.interfaceArguments() }
            } else {
                listOf(expression)
            }
        else -> listOf(expression)
    }

private val MORE_INTERFACES = Name.identifier("moreInterfaces")
private val TYPE = Name.identifier("type")
private val ADDITIONAL_INTERFACE = IntrinsicKind.ADDITIONAL_INTERFACE
private const val SPY_VAL_HINT =
    ". A 'val' can be a spy only with a constructor call of an open class as the initializer, such as 'val car = Car()'"
private val SPIED_INSTANCE = Name.identifier("obj")
private val ARRAY_OF = CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("arrayOf"))
private val DO_NOT_MOCK = Name.identifier("DoNotMock")
private val VALUE = Name.identifier("value")
private val RESTRICTED_TYPES = setOf(
    StandardClassIds.Collection,
    ClassId.fromString("java/util/HashMap"),
    ClassId.fromString("java/io/File"),
    ClassId.fromString("java/nio/file/Path"),
)
