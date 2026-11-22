package io.lokki.compiler.fir.checkers

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ParameterNames
import io.lokki.compiler.fir.MemberInterception
import io.lokki.compiler.fir.displayName
import io.lokki.compiler.fir.staticInterception
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.utils.isStatic
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
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
import org.jetbrains.kotlin.name.FqName

context(context: CheckerContext, reporter: DiagnosticReporter)
internal fun checkStaticReferences(call: FirFunctionCall, function: FirCallableSymbol<*>) {
    val name = function.name.asString()
    for ((argument, parameter) in call.resolvedArgumentMapping.orEmpty()) {
        if (parameter.name.asString() !in ParameterNames.REFERENCES) continue
        val references = (argument as? FirVarargArgumentsExpression)?.arguments ?: listOf(argument)
        references.forEach { checkStaticReference(it, name) }
    }
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
