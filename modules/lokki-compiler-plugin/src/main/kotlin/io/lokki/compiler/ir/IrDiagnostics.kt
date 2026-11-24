package io.lokki.compiler.ir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.collect.DelegatingSpy
import io.lokki.compiler.ir.collect.ExternalMocks
import io.lokki.compiler.ir.collect.FailedInjection
import io.lokki.compiler.ir.collect.InjectionProblem
import io.lokki.compiler.ir.collect.MockFields
import io.lokki.compiler.ir.collect.MockProblem
import io.lokki.compiler.ir.collect.MockSite
import io.lokki.compiler.ir.collect.MockSites
import io.lokki.compiler.ir.collect.NotMockable
import io.lokki.compiler.ir.collect.UnsafeSpyDelegation
import io.lokki.compiler.ir.collect.Unsupported
import io.lokki.compiler.ir.collect.constructorParameter
import io.lokki.compiler.ir.collect.mockAnnotationName
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

internal class IrDiagnostics(private val lokki: LokkiIrContext, private val file: IrFile) {
    fun unsupported(element: IrElement, reason: String) {
        at(element).report(LokkiDiagnostics.LOKKI_UNSUPPORTED, reason)
    }

    fun notInterceptable(element: IrElement, name: String, reason: String) {
        at(element).report(LokkiDiagnostics.LOKKI_OBJECT_NOT_INTERCEPTABLE, name, reason)
    }

    fun notInterceptableMember(element: IrElement, name: String, reason: String) {
        at(element).report(LokkiDiagnostics.LOKKI_NOT_INTERCEPTABLE, name, reason)
    }

    fun unresolvedDynamicCall(element: IrElement, reason: String) {
        at(element).report(LokkiDiagnostics.LOKKI_UNRESOLVED_DYNAMIC_CALL, reason)
    }

    fun report(fields: MockFields, sites: MockSites) {
        for (parameter in fields.unsupportedParameters) {
            unsupported(
                parameter,
                "'@${parameter.mockAnnotationName}' works only on parameters of methods and constructors of a class",
            )
        }
        for (field in fields.parameterGroups.flatMap { it.parameters }) {
            field.problem?.let { report(field.parameter, it) }
            field.site?.warning?.let { report(field.parameter, it) }
        }
        for (field in fields.fields.values) {
            val problem = field.problem
            when {
                problem == null -> field.site?.warning?.let { report(field.property, it) }
                field.property.constructorParameter?.mockAnnotationName == null -> report(field.property, problem)
            }
        }
        for ((call, site) in sites.calls) {
            when (site) {
                is MockSite.Mocked -> site.warning?.let { report(call, it) }
                is MockSite.Rejected -> report(call, site.problem)
                MockSite.Reified -> {}
            }
        }
    }

    fun report(external: ExternalMocks) {
        for (annotation in external.misplaced) {
            unsupported(annotation, "'@${annotation.annotationClass?.name}' works only on properties of classes")
        }
        for (field in external.fields.values) (field.site as? MockSite.Rejected)?.let { report(field.annotation, it.problem) }
    }

    fun report(element: IrElement, problem: MockProblem) {
        val at = at(problem.element ?: element)
        when (problem) {
            is NotMockable -> when {
                problem.isFrontendRule -> at.report(LokkiDiagnostics.LOKKI_UNSUPPORTED, problem.message)
                else -> at.report(LokkiDiagnostics.LOKKI_TYPE_NOT_MOCKABLE, problem.type, problem.reason)
            }
            is UnsafeSpyDelegation -> at.report(
                LokkiDiagnostics.LOKKI_UNSAFE_SPY_DELEGATION,
                problem.type,
                problem.members.joinToString { "'$it'" },
                problem.name,
            )
            is DelegatingSpy ->
                at.report(LokkiDiagnostics.LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL, problem.type, problem.name)
            is Unsupported -> at.report(LokkiDiagnostics.LOKKI_UNSUPPORTED, problem.reason)
            is FailedInjection -> {
                val factory = when (problem.problem.kind) {
                    InjectionProblem.Kind.UNRESOLVED -> LokkiDiagnostics.LOKKI_UNRESOLVED_INJECTION
                    InjectionProblem.Kind.AMBIGUOUS -> LokkiDiagnostics.LOKKI_AMBIGUOUS_INJECTION
                    InjectionProblem.Kind.UNSUPPORTED -> LokkiDiagnostics.LOKKI_UNSUPPORTED_INJECTION
                }
                at.report(factory, problem.type, problem.problem.message)
            }
        }
    }

    private fun at(element: IrElement): IrDiagnosticReporter.IrDiagnosticContext =
        lokki.compat.diagnosticContext(lokki.pluginContext.diagnosticReporter, element, file)
}
