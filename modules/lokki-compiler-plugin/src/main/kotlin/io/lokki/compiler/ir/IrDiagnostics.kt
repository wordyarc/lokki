package io.lokki.compiler.ir

import io.lokki.compiler.LokkiDiagnostics
import io.lokki.compiler.ir.collect.DelegatingSpy
import io.lokki.compiler.ir.collect.FailedInjection
import io.lokki.compiler.ir.collect.InjectionProblem
import io.lokki.compiler.ir.collect.MockProblem
import io.lokki.compiler.ir.collect.NotMockable
import io.lokki.compiler.ir.collect.Unsupported
import io.lokki.compiler.ir.collect.UnsafeSpyDelegation
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile

internal class IrDiagnostics(private val lokki: LokkiIrContext, private val file: IrFile) {
    fun unsupported(element: IrElement, reason: String) {
        at(element).report(LokkiDiagnostics.LOKKI_UNSUPPORTED, reason)
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
