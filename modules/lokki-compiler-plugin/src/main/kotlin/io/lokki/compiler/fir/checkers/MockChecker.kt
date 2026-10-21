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
import org.jetbrains.kotlin.descriptors.java.JavaVisibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaredMemberScope
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.canSeeInternalsOf
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.isAbstract
import org.jetbrains.kotlin.fir.declarations.utils.isFinal
import org.jetbrains.kotlin.fir.declarations.utils.isInner
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
import org.jetbrains.kotlin.fir.resolve.scope
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.CallableCopyTypeCalculator
import org.jetbrains.kotlin.fir.scopes.firOverrideChecker
import org.jetbrains.kotlin.fir.scopes.getDeclaredConstructors
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.getProperties
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.scopes.isOverriddenFunction
import org.jetbrains.kotlin.fir.scopes.isOverriddenProperty
import org.jetbrains.kotlin.fir.scopes.processAllCallables
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.contains
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.AbstractTypeChecker

internal object MockChecker : FirFunctionCallChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val function = expression.calleeReference.toResolvedCallableSymbol() ?: return
        val kind = function.intrinsicKind(context.session)
        if (kind != IntrinsicKind.MOCK && kind != IntrinsicKind.SPY) return
        val instance = if (kind == IntrinsicKind.SPY) spiedInstance(expression, function) else null
        val constructed = instance?.constructedType()
        val argument = expression.typeArguments.singleOrNull() ?: return
        val source = when {
            instance != null -> instance.source
            else -> argument.source?.takeIf { it.kind is KtRealSourceElementKind } ?: expression.source
        }
        val type = constructed
            ?: argument.toConeTypeProjection().type?.lowerBoundIfFlexible()?.fullyExpandedType()
            ?: return
        if (type is ConeTypeParameterType) {
            reporter.reportOn(
                source,
                LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
                "The type argument",
                function.name.asString(),
                "'${type.lookupTag.name}' is a type parameter",
            )
            return
        }
        val mocked = type.takeUnless { it.isNothing }?.toRegularClassSymbol(context.session) ?: return
        val reason = mocked.notMockableReason()
            ?: (type as? ConeClassLikeType)?.irreplaceableStarProjection(mocked)
            ?: mocked.unconstructibleReason().takeIf { kind == IntrinsicKind.SPY && instance == null }
        if (reason != null) {
            reporter.reportOn(source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, reason)
            return
        }
        if (instance != null && constructed == null && mocked.classKind == ClassKind.CLASS) {
            val members = mocked.unforwardableMembers()
            if (members.isNotEmpty()) {
                reporter.reportOn(
                    instance.source,
                    LokkiDiagnostics.LOKKI_UNSAFE_SPY_DELEGATION,
                    mocked.displayName,
                    members.joinToString { "'$it'" },
                    mocked.name.asString(),
                )
                return
            }
            reporter.reportOn(
                instance.source,
                LokkiDiagnostics.LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL,
                mocked.displayName,
                mocked.name.asString(),
            )
        }
        val interfaces = moreInterfaces(expression, function) ?: return
        val mockedType = (type as? ConeClassLikeType)?.approximated(mocked) ?: return
        val conflict = mockedType.addedInterfaces(interfaces).firstNotNullOfOrNull { (literal, supertypes) ->
            supertypes.last().conflict(supertypes.dropLast(1))?.let { literal to it }
        }
        if (conflict != null) {
            val (literal, problem) = conflict
            reporter.reportOn(literal.source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, mocked.displayName, problem)
            return
        }
        if (kind == IntrinsicKind.SPY) return
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
internal fun ConeKotlinType.childMockProblem(): Pair<String, String>? {
    val type = when (val expanded = fullyExpandedType().lowerBoundIfFlexible()) {
        is ConeDefinitelyNotNullType -> expanded.original
        else -> expanded
    }
    if (type is ConeTypeParameterType) return type.lookupTag.name.asString() to "it is a type parameter"
    val mocked = type.toRegularClassSymbol(context.session) ?: return type.renderReadable() to "it is not a class"
    val reason = mocked.notMockableReason()
        ?: (type as? ConeClassLikeType)?.irreplaceableStarProjection(mocked)
        ?: return null
    return mocked.displayName to reason
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.notMockableReason(): String? =
    when {
        classKind == ClassKind.OBJECT -> "it is an object"
        classKind == ClassKind.ENUM_CLASS || classKind == ClassKind.ENUM_ENTRY -> "it is an enum class"
        classKind == ClassKind.ANNOTATION_CLASS -> "it is an annotation class"
        modality == Modality.SEALED -> "it is sealed"
        isFinalType -> "it is a final class. Extract an interface or make the class and its members open"
        classKind == ClassKind.CLASS && declaredMemberScope().getDeclaredConstructors().none { it.isVisibleToMock() } ->
            "none of its constructors is visible from this file"
        else -> missingSupertypes().takeIf { it.isNotEmpty() }?.let { supertypes ->
            "its supertypes are missing from the classpath: ${supertypes.joinToString()}. Add the dependency with " +
                "these classes to the test compilation and runtime, e.g. with 'testImplementation'"
        } ?: hiddenAbstractMembers().takeIf { it.isNotEmpty() }?.let { members ->
            "a mock cannot implement its abstract members that are not visible from this file: " +
                members.joinToString { "'$it'" }
        } ?: uninheritedAbstractMembers().takeIf { it.isNotEmpty() }?.let { members ->
            "a mock cannot implement its package-private abstract members that it inherits through a class of " +
                "another package: ${members.joinToString { "'$it'" }}"
        } ?: membersWithMissingClasses().takeIf { it.isNotEmpty() }?.let { members ->
            "it or its supertypes declare members that refer to classes missing from the classpath: " +
                "${members.joinToString()}. Add the dependency with these classes to the test compilation and " +
                "runtime, e.g. with 'testImplementation'"
        } ?: membersWithInaccessibleClasses()
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
private fun FirRegularClassSymbol.unconstructibleReason(): String? {
    if (classKind != ClassKind.CLASS) return null
    val constructible = !isInner && declaredMemberScope().getDeclaredConstructors().any { constructor ->
        constructor.isVisibleToMock() && constructor.valueParameterSymbols.all { it.hasDefaultValue }
    }
    if (constructible) return null
    return "none of its constructors can be called without arguments. Pass a constructor call to 'spy', " +
        "such as 'spy(${name}(...))'"
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.unforwardableMembers(): List<String> {
    val members = sortedSetOf<String>()
    unsubstitutedScope().processAllCallables { member ->
        if (member.visibility == Visibilities.Private) return@processAllCallables
        if (member.isFinal || member.visibility in PROTECTED || !member.isVisibleToMock()) {
            members += member.name.asString()
        }
    }
    return members.toList()
}

private fun ConeClassLikeType.irreplaceableStarProjection(mocked: FirRegularClassSymbol): String? {
    val stars = mocked.typeParameterSymbols.filterIndexed { index, _ -> typeArguments.getOrNull(index) is ConeStarProjection }
    val parameter = stars.firstOrNull { star ->
        val bounds = star.resolvedBounds
        bounds.size > 1 ||
            bounds.single().coneType.contains { it is ConeTypeParameterType && it.lookupTag.typeParameterSymbol in stars }
    } ?: return null
    return "the star projection for '${parameter.name}' cannot be replaced with the upper bound of '${parameter.name}'. " +
        "Specify the type argument"
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
private fun FirRegularClassSymbol.missingSupertypes(): List<String> =
    lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .filter { it.lookupTag.toSymbol(context.session) == null }
        .map { it.lookupTag.classId.asFqNameString() }
        .distinct()

context(context: CheckerContext)
private fun FirRegularClassSymbol.uninheritedAbstractMembers(): List<String> {
    val superclasses = generateSequence(this) { type ->
        type.resolvedSuperTypes.firstNotNullOfOrNull { supertype ->
            supertype.toRegularClassSymbol(context.session)?.takeIf { it.classKind == ClassKind.CLASS }
        }
    }.toList()
    val members = sortedSetOf<String>()
    unsubstitutedScope().processAllCallables { member ->
        val declaration = member.unwrapFakeOverrides()
        val owner = declaration.callableId?.classId ?: return@processAllCallables
        if (member.isAbstract && declaration.visibility == JavaVisibilities.PackageVisibility &&
            superclasses.takeWhile { it.classId != owner }.any { it.classId.packageFqName != owner.packageFqName }
        ) {
            members += member.name.asString()
        }
    }
    return members.toList()
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.membersWithMissingClasses(): List<String> {
    val members = sortedSetOf<String>()
    val supertypes = lookupSuperTypes(this, lookupInterfaces = true, deep = true, context.session)
        .mapNotNull { it.toRegularClassSymbol(context.session) }
    for (type in listOf(this) + supertypes) {
        type.unsubstitutedScope().processAllCallables { member ->
            val missing = member.erasedClasses().filter { it.lookupTag.toSymbol(context.session) == null }
            if (missing.isNotEmpty()) members += member.withClasses(missing)
        }
    }
    return members.toList()
}

context(context: CheckerContext)
private fun FirRegularClassSymbol.membersWithInaccessibleClasses(): String? {
    val filePackage = context.filePackage ?: return null
    val members = sortedSetOf<String>()
    val packages = sortedSetOf<String>()
    unsubstitutedScope().processAllCallables { member ->
        if (member.isFinal || !member.isVisibleToMock()) return@processAllCallables
        val inaccessible = member.erasedClasses().filter { type ->
            val symbol = type.toRegularClassSymbol(context.session) ?: return@filter false
            symbol.visibility in PACKAGE_PRIVATE_CLASSES && symbol.classId.packageFqName != filePackage
        }
        if (inaccessible.isNotEmpty()) {
            members += member.withClasses(inaccessible)
            inaccessible.mapTo(packages) { it.lookupTag.classId.packageFqName.asString() }
        }
    }
    if (members.isEmpty()) return null
    val advice = packages.singleOrNull()?.let { ". Create the mock in a file of package '$it'" }.orEmpty()
    return "a mock cannot override its members that refer to package-private classes of another package: " +
        members.joinToString() + advice
}

private fun FirCallableSymbol<*>.withClasses(classes: List<ConeClassLikeType>): String =
    "'$name' (${classes.joinToString { it.lookupTag.classId.asFqNameString() }})"

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
            type.lookupTag.typeParameterSymbol.resolvedBounds.flatMap { it.coneType.erasedClasses() }
        is ConeDefinitelyNotNullType -> type.original.erasedClasses()
        else -> emptyList()
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

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun moreInterfaces(
    call: FirFunctionCall,
    function: FirCallableSymbol<*>,
): List<Pair<FirExpression, ConeClassLikeType>>? {
    val parameter = (function as? FirFunctionSymbol<*>)?.valueParameterSymbols?.find { it.name == MORE_INTERFACES }
        ?: return emptyList()
    val argument = call.resolvedArgumentMapping?.entries?.find { it.value.symbol == parameter }?.key
        ?: return emptyList()
    val interfaces = ArrayList<Pair<FirExpression, ConeClassLikeType>>()
    var valid = true
    for (element in argument.interfaceArguments()) {
        val target = (element as? FirGetClassCall)?.getTargetType()?.fullyExpandedType()
        val notConcrete = when (target) {
            null -> "it is not a class literal such as 'Closeable::class'"
            is ConeTypeParameterType -> "'${target.lookupTag.name}' is a type parameter"
            else -> null
        }
        if (notConcrete != null) {
            reporter.reportOn(
                element.source,
                LokkiDiagnostics.LOKKI_TYPE_ARGUMENT_NOT_CONCRETE,
                "An interface in 'moreInterfaces'",
                function.name.asString(),
                notConcrete,
            )
            valid = false
            continue
        }
        val symbol = target?.toRegularClassSymbol(context.session) ?: continue
        val reason = if (symbol.classKind != ClassKind.INTERFACE) {
            "it is not an interface, and 'moreInterfaces' takes only interfaces"
        } else {
            symbol.notMockableReason() ?: (target as? ConeClassLikeType)?.irreplaceableStarProjection(symbol)
        }
        if (reason != null) {
            reporter.reportOn(element.source, LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, symbol.displayName, reason)
            valid = false
            continue
        }
        interfaces += element to (target as ConeClassLikeType).approximated(symbol)
    }
    return interfaces.takeIf { valid }
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

context(context: CheckerContext)
private fun ConeClassLikeType.approximated(symbol: FirRegularClassSymbol): ConeClassLikeType {
    val parameters = symbol.typeParameterSymbols
    val explicit = parameters.zip(typeArguments).mapNotNull { (parameter, argument) ->
        argument.type?.let { parameter to it }
    }.toMap()
    val substitutor = substitutorByMap(explicit, context.session)
    val arguments = parameters.mapIndexed { index, parameter ->
        typeArguments.getOrNull(index)?.type
            ?: substitutor.substituteOrSelf(parameter.resolvedBounds.single().coneType)
    }
    return symbol.constructType(arguments.toTypedArray())
}

context(context: CheckerContext)
private fun ConeClassLikeType.addedInterfaces(
    interfaces: List<Pair<FirExpression, ConeClassLikeType>>,
): List<Pair<FirExpression, List<ConeClassLikeType>>> {
    val distinct = interfaces.distinctBy { it.second.lookupTag }
    val all = listOf(this) + distinct.map { it.second }
    val added = distinct.filter { (_, extra) ->
        all.none { other ->
            other !== extra && extra.lookupTag in (listOf(other) + other.supertypes()).map { it.lookupTag }
        }
    }
    return added.mapIndexed { index, (literal, extra) ->
        literal to listOf(this) + added.take(index).map { it.second } + extra
    }
}

context(context: CheckerContext)
private fun ConeClassLikeType.supertypes(): List<ConeClassLikeType> {
    val symbol = toRegularClassSymbol(context.session) ?: return emptyList()
    val substitutor = substitutorByMap(
        symbol.typeParameterSymbols.zip(typeArguments.mapNotNull { it.type }).toMap(),
        context.session,
    )
    val supertypes = lookupSuperTypes(
        listOf(symbol),
        lookupInterfaces = true,
        deep = true,
        context.session,
        substituteTypes = true,
    )
    return supertypes.map { substitutor.substituteOrSelf(it) as ConeClassLikeType }
}

context(context: CheckerContext)
private fun ConeClassLikeType.conflict(previous: List<ConeClassLikeType>): String? {
    val inherited = previous.flatMap { listOf(it) + it.supertypes() }
    for (supertype in listOf(this) + supertypes()) {
        val other = inherited.find { it.lookupTag == supertype.lookupTag && it != supertype } ?: continue
        return "it would inherit '${supertype.lookupTag.name}' with different type arguments: " +
            "'${other.renderReadable()}' and '${supertype.renderReadable()}'"
    }
    return previous.firstNotNullOfOrNull { memberConflict(it, this) }
}

context(context: CheckerContext)
private fun memberConflict(first: ConeClassLikeType, second: ConeClassLikeType): String? {
    val session = context.session
    val scopes = listOf(first, second).map { type ->
        type.scope(session, context.scopeSession, CallableCopyTypeCalculator.DoNothing, FirResolvePhase.STATUS)
            ?: return null
    }
    val checker = session.firOverrideChecker
    for (name in scopes[0].getCallableNames() intersect scopes[1].getCallableNames()) {
        val functions = scopes[0].getFunctions(name).any { left ->
            scopes[1].getFunctions(name).any { right ->
                checker.isOverriddenFunction(left, right) && !left.isCompatibleWith(right)
            }
        }
        val properties = scopes[0].getProperties(name).filterIsInstance<FirPropertySymbol>().any { left ->
            scopes[1].getProperties(name).filterIsInstance<FirPropertySymbol>().any { right ->
                checker.isOverriddenProperty(left, right) && !left.isCompatibleWith(right)
            }
        }
        if (functions || properties) {
            val owners = "'${first.lookupTag.name}' and '${second.lookupTag.name}'"
            return "its member '$name' has incompatible types in $owners"
        }
    }
    return null
}

context(context: CheckerContext)
private fun FirCallableSymbol<*>.isCompatibleWith(other: FirCallableSymbol<*>): Boolean {
    val substitutor = substitutorByMap(
        other.typeParameterSymbols.zip(typeParameterSymbols.map { it.toConeType() }).toMap(),
        context.session,
    )
    val type = resolvedReturnType
    val otherType = substitutor.substituteOrSelf(other.resolvedReturnType)
    return isMoreSpecific(type, other, otherType) || other.isMoreSpecific(otherType, this, type)
}

context(context: CheckerContext)
private fun FirCallableSymbol<*>.isMoreSpecific(
    type: ConeKotlinType,
    other: FirCallableSymbol<*>,
    otherType: ConeKotlinType,
): Boolean {
    val typeContext = context.session.typeContext
    val otherIsVar = other is FirPropertySymbol && other.isVar
    return when {
        otherIsVar -> this is FirPropertySymbol && isVar && AbstractTypeChecker.equalTypes(typeContext, type, otherType)
        else -> AbstractTypeChecker.isSubtypeOf(typeContext, type, otherType)
    }
}

private val PACKAGE_PRIVATE_CLASSES = setOf(Visibilities.Private, JavaVisibilities.PackageVisibility)
private val PROTECTED = setOf(
    Visibilities.Protected,
    JavaVisibilities.ProtectedAndPackage,
    JavaVisibilities.ProtectedStaticVisibility,
)
private val MORE_INTERFACES = Name.identifier("moreInterfaces")
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
