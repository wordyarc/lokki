package io.lokki.compiler.fir.checkers

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.fir.MemberInterception
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.interceptionProblem
import io.lokki.compiler.fir.staticInterception
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.getTargetType
import org.jetbrains.kotlin.fir.declarations.isJavaOrEnhancement
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkStaticReferences(call: FirFunctionCall, function: FirCallableSymbol<*>) {
    val name = function.name.asString()
    for ((argument, parameter) in call.resolvedArgumentMapping.orEmpty()) {
        if (parameter.name.asString() == ParameterNames.FILE_CLASSES) {
            ((argument as? FirVarargArgumentsExpression)?.arguments ?: listOf(argument)).forEach {
                when {
                    it.resolvedType.fullyExpandedType().classId == StandardClassIds.KClass -> checkStaticClass(it, name)
                    (it as? FirLiteralExpression)?.value !is String ->
                        reporter.reportOn(it.source, LokkiDiagnostics.LOKKI_FILE_CLASS_NOT_LITERAL, name)
                }
            }
            continue
        }
        if (parameter.name.asString() !in ParameterNames.REFERENCES) continue
        val references = (argument as? FirVarargArgumentsExpression)?.arguments ?: listOf(argument)
        references.forEach { checkStaticReference(it, name) }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkStaticClass(argument: FirExpression, function: String) {
    val symbol = (argument as? FirGetClassCall)?.getTargetType()?.fullyExpandedType()?.toRegularClassSymbol()
    val companion = symbol?.resolvedCompanionObjectSymbol
    val reason = when {
        argument !is FirGetClassCall -> "pass class literals such as 'Clock::class': '$function' chooses them at compile time"
        symbol == null -> "it is not a class"
        symbol.isJavaOrEnhancement ->
            "it is a Java class, whose static methods lokki does not intercept. Inject the dependency or call it " +
                "through a Kotlin object from lokki { interceptable(...) }"
        symbol.classKind == ClassKind.OBJECT -> symbol.interceptionProblem()?.let { "it $it" }
        companion != null -> companion.interceptionProblem()?.let { "its companion object $it" }
        else -> "it has no static members: only an object and a companion object of a Kotlin class have them"
    } ?: return
    val name = symbol?.displayName ?: argument.resolvedType.renderReadable()
    reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE, name, reason)
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun checkStaticReference(argument: FirExpression, function: String) {
    val reference = argument as? FirCallableReferenceAccess
    val receiver = reference?.explicitReceiver
    val bound = receiver != null && receiver !is FirResolvedQualifier
    if (argument is FirSpreadArgumentExpression || reference == null || bound) {
        reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_REFERENCE_NOT_LITERAL, function)
        return
    }
    val callee = reference.calleeReference.toResolvedCallableSymbol() ?: return
    val reason = when (val interception = callee.staticInterception()) {
        MemberInterception.Intercepted -> return
        is MemberInterception.NotIntercepted -> interception.reason
        MemberInterception.NotApplicable -> memberReason(callee)
    }
    val name = callee.name.asString()
    reporter.reportOn(argument.source, LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE, name, reason)
}

context(context: CheckerContext)
private fun memberReason(callee: FirCallableSymbol<*>): String {
    val owner = callee.getContainingClassSymbol() as? FirRegularClassSymbol
    return when {
        owner == null -> "it is local"
        callee.isStatic -> "it is a static member of a Java class, which lokki does not intercept"
        owner.classKind == ClassKind.OBJECT ->
            "it is a member of '${owner.displayName}': mock the object with 'mockObject(${owner.name})'"
        else -> "it is a member of '${owner.displayName}': " + when (val extension = callee.sameNamedExtension(owner)) {
            null -> "mock its instance with 'mock<T>()' or 'spy(...)'"
            else -> "the reference resolves to the member, not to the extension '$extension'. Import the extension " +
                "under another name, such as 'import $extension as ${callee.name}Extension'"
        }
    }
}

@OptIn(SymbolInternals::class)
context(context: CheckerContext)
private fun FirCallableSymbol<*>.sameNamedExtension(owner: FirRegularClassSymbol): FqName? {
    val file = context.containingFileSymbol?.fir ?: return null
    val imports = file.imports.filter { it.aliasName == null }
    val packages = listOf(file.packageFqName) +
        imports.filter { it.isAllUnder }.mapNotNull { it.importedFqName } +
        imports.mapNotNull { it.importedFqName?.takeIf { fqName -> fqName.shortName() == name }?.parent() }
    val receivers = listOf(owner) + lookupSuperTypes(owner, lookupInterfaces = true, deep = true, context.session)
        .mapNotNull { it.toRegularClassSymbol(context.session) }
    return packages.distinct().firstNotNullOfOrNull { packageName ->
        context.session.symbolProvider.getTopLevelCallableSymbols(packageName, name)
            .find { it.resolvedReceiverType?.toRegularClassSymbol(context.session) in receivers }
            ?.let { packageName.child(name) }
    }
}
