package io.lokki.compiler.fir.checkers

import io.lokki.compiler.ARRAY_OF
import io.lokki.compiler.IntrinsicKind
import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.NOT_CLASS_PROBLEM
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.TYPE_PARAMETER_PROBLEM
import io.lokki.compiler.fir.argument
import io.lokki.compiler.fir.classifier
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.effectiveModality
import io.lokki.compiler.fir.externalKind
import io.lokki.compiler.fir.fieldKind
import io.lokki.compiler.fir.finalMembers
import io.lokki.compiler.fir.intrinsicKind
import io.lokki.compiler.fir.isFromFriendKotlinModule
import io.lokki.compiler.fir.isOpenedInBytecode
import io.lokki.compiler.fir.mockProblem
import io.lokki.compiler.fir.mockabilityProblem
import io.lokki.compiler.fir.restriction
import io.lokki.compiler.linked
import io.lokki.compiler.notInterfaceProblem
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirValueParameterChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.contains
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.Name

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkMock(call: FirFunctionCall, function: FirCallableSymbol<*>, kind: IntrinsicKind) {
    val instance = if (kind == IntrinsicKind.SPY) call.argument(ParameterNames.SPIED_INSTANCE) else null
    val argument = call.typeArguments.singleOrNull() ?: return
    if (kind == IntrinsicKind.MOCK && !call.hasMockedClassLiteral(function)) return
    val source = when {
        instance != null -> instance.source
        else -> argument.source?.takeIf { it.kind is KtRealSourceElementKind } ?: call.source
    }
    val type = instance?.constructedType()
        ?: argument.toConeTypeProjection().type?.lowerBoundIfFlexible()?.fullyExpandedType()
        ?: return
    if (kind == IntrinsicKind.MOCK && type.isReifiedHelperParameter && !call.hasMoreInterfaces()) return
    val mocked = MockedType(kind, type, source, "The type argument", function.name.asString())
    mocked.check { moreInterfaces(call, function) }
}

context(context: CheckerContext)
private val ConeKotlinType.isReifiedHelperParameter: Boolean
    get() {
        val parameter = (this as? ConeTypeParameterType)?.toSymbol(context.session) as? FirTypeParameterSymbol
        return parameter?.isReified == true && parameter.containingDeclarationSymbol is FirNamedFunctionSymbol
    }

private fun FirFunctionCall.hasMoreInterfaces(): Boolean {
    val interfaces = argument(ParameterNames.MORE_INTERFACES)?.unwrapArgument() ?: return false
    return (interfaces as? FirVarargArgumentsExpression)?.arguments?.isNotEmpty() != false
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun FirFunctionCall.hasMockedClassLiteral(function: FirCallableSymbol<*>): Boolean {
    val literal = argument(ParameterNames.MOCKED_CLASS) ?: return true
    if (literal.unwrapArgument() is FirGetClassCall) return true
    reporter.reportOn(
        literal.source,
        LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
        "The class",
        function.name.asString(),
        "it is not a class literal such as 'Car::class'",
    )
    return false
}

internal object MockPropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) = linked { inspect(declaration) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(declaration: FirProperty) {
        val session = context.session
        val external = (declaration.annotations + declaration.backingField?.annotations.orEmpty())
            .firstNotNullOfOrNull { annotation -> annotation.externalKind(session)?.let { annotation to it } }
        if (external != null) return inspectExternal(declaration, external.first, external.second)
        val kinds = declaration.annotations.mapNotNull { annotation ->
            annotation.fieldKind(session)?.let { annotation to it }
        }
        val inject = kinds.find { (_, kind) -> kind == IntrinsicKind.INJECT_MOCKS }?.first
        val (annotation, kind) = kinds.find { (candidate, _) -> candidate !== inject } ?: kinds.firstOrNull() ?: return
        checkRuntimeVersion(annotation.source)
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
                reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_MOCK_PROPERTY_NOT_MUTABLE, name, LOKKI, "")
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
            reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_MOCK_PROPERTY_NOT_MUTABLE, name, LOKKI, hint)
        }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun inspectExternal(declaration: FirProperty, annotation: FirAnnotation, kind: IntrinsicKind) {
    checkRuntimeVersion(annotation.source)
    val name = annotation.toAnnotationClassLikeSymbol(context.session)!!.name.asString()
    val type = declaration.returnTypeRef.coneType.lowerBoundIfFlexible().fullyExpandedType()
    val mocked = MockedType(
        if (kind == IntrinsicKind.EXTERNAL_SPY) IntrinsicKind.SPY else IntrinsicKind.MOCK,
        type,
        annotation.source,
        "The mocked type",
        "@$name",
    )
    if (!mocked.check { true }) return
    if (!declaration.isVar) {
        reporter.reportOn(
            annotation.source,
            LokkiDiagnostics.LOKKI_MOCK_PROPERTY_NOT_MUTABLE,
            name,
            TEST_FRAMEWORK,
            "",
        )
    }
}

private const val LOKKI = "lokki"

private const val TEST_FRAMEWORK = "the test framework that reads the annotation"

internal object MockParameterChecker : FirValueParameterChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirValueParameter) = linked { inspect(declaration) }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun inspect(declaration: FirValueParameter) {
        val session = context.session
        val kinds = declaration.annotations.mapNotNull { annotation ->
            annotation.fieldKind(session)?.let { annotation to it }
        }
        val inject = kinds.find { (_, kind) -> kind == IntrinsicKind.INJECT_MOCKS }?.first
        val (annotation, kind) = kinds.find { (candidate, _) -> candidate !== inject } ?: kinds.firstOrNull() ?: return
        checkRuntimeVersion(annotation.source)
        val name = annotation.toAnnotationClassLikeSymbol(session)!!.name.asString()
        if (!declaration.containingDeclarationSymbol.takesMockParameters()) {
            reporter.reportOn(annotation.source, LokkiDiagnostics.LOKKI_UNSUPPORTED_MOCK_PARAMETER, name)
            return
        }
        val type = declaration.returnTypeRef.coneType.lowerBoundIfFlexible().fullyExpandedType()
        if (inject != null && kind != IntrinsicKind.INJECT_MOCKS && kind != IntrinsicKind.SPY) {
            reporter.reportOn(
                inject.source,
                LokkiDiagnostics.LOKKI_UNSUPPORTED_INJECTION,
                type.toRegularClassSymbol(session)?.displayName ?: type.renderReadable(),
                "a parameter cannot have both '@InjectMocks' and '@$name'",
            )
            return
        }
        if (kind == IntrinsicKind.INJECT_MOCKS) return
        val mocked = MockedType(
            if (kind == IntrinsicKind.SPY) kind else IntrinsicKind.MOCK,
            type,
            annotation.source,
            "The mocked type",
            "@$name",
        )
        mocked.check { additionalInterface(declaration) }
    }
}

context(context: CheckerContext)
private fun FirBasedSymbol<*>.takesMockParameters(): Boolean =
    (this is FirConstructorSymbol || this is FirNamedFunctionSymbol) && getContainingClassSymbol()?.isLocal == false

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
    val problem = if (kind == IntrinsicKind.MOCK) mocked.mockProblem() else mocked.mockabilityProblem()
    if (problem != null) {
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, problem)
        return false
    }
    if (!interfaces()) return false
    if (kind == IntrinsicKind.SPY) return true
    mocked.restriction()?.let { restriction ->
        reporter.reportOn(source, LokkiDiagnostics.LOKKI_RESTRICTED_TYPE, mocked.displayName, restriction)
    }
    val finalMembers = mocked.finalMembers()
    if (finalMembers.isNotEmpty()) {
        val advice = when {
            mocked.isOpenedInBytecode(context.session) ->
                "lokki { mockable(...) } does not open inline and tailrec functions, @JvmField properties and " +
                    "members inherited from classes it does not open"
            mocked.isFromFriendKotlinModule(context.session) ->
                "Make them open or add their classes to lokki { mockable(...) }"
            else -> "Make them open"
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
    return mocked.mockProblem()?.let { mocked.displayName to it }
}

context(context: CheckerContext)
private fun FirExpression.constructedType(): ConeKotlinType? {
    val call = unwrapArgument() as? FirFunctionCall ?: return null
    if (call.calleeReference.toResolvedCallableSymbol() !is FirConstructorSymbol) return null
    val type = call.resolvedType.fullyExpandedType()
    val constructed = type.toRegularClassSymbol(context.session) ?: return null
    return type.takeIf {
        constructed.classKind == ClassKind.CLASS &&
            constructed.effectiveModality(context.session).let { it != Modality.FINAL && it != Modality.SEALED }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun moreInterfaces(call: FirFunctionCall, function: FirCallableSymbol<*>): Boolean {
    val argument = call.argument(ParameterNames.MORE_INTERFACES) ?: return true
    val dsl = function.name.asString()
    return interfaces(argument.interfaceArguments(), "An interface in 'moreInterfaces'", dsl, "'moreInterfaces'")
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun additionalInterface(declaration: FirAnnotationContainer): Boolean {
    val annotation = declaration.annotations
        .find { it.toAnnotationClassLikeSymbol(context.session)?.intrinsicKind(context.session) == ADDITIONAL_INTERFACE }
        ?: return true
    val type = annotation.findArgumentByName(INTERFACE_TYPE) ?: return true
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
        val reason = if (symbol.classKind != ClassKind.INTERFACE) notInterfaceProblem(owner) else symbol.mockabilityProblem()
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

private val INTERFACE_TYPE = Name.identifier(ParameterNames.INTERFACE_TYPE)
private val ADDITIONAL_INTERFACE = IntrinsicKind.ADDITIONAL_INTERFACE
private const val SPY_VAL_HINT =
    ". A 'val' can be a spy only with a constructor call of an open class as the initializer, such as 'val car = Car()'"
