package io.lokki.compiler.test.services

import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.test.backend.handlers.AbstractIrHandler
import org.jetbrains.kotlin.test.backend.handlers.assertFileDoesntExist
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.ServiceRegistrationData
import org.jetbrains.kotlin.test.services.TestService
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlin.test.services.service
import org.jetbrains.kotlin.test.utils.withExtension

internal class SourcelessDiagnostics : TestService {
    private val rendered = ArrayList<String>()

    fun collect(module: TestModule, collector: BaseDiagnosticsCollector) {
        collector.diagnosticsByFile[null].orEmpty().mapTo(rendered) { diagnostic ->
            "${module.name}: ${diagnostic.severity.name.lowercase()}: [${diagnostic.factoryName}] " +
                diagnostic.renderMessage()
        }
    }

    fun dump(): String = rendered.joinToString("\n")
}

private val TestServices.sourcelessDiagnostics: SourcelessDiagnostics by TestServices.testServiceAccessor()

internal class IrSourcelessDiagnosticsHandler(testServices: TestServices) : AbstractIrHandler(testServices) {
    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::SourcelessDiagnostics))

    override fun processModule(module: TestModule, info: IrBackendInput) {
        testServices.sourcelessDiagnostics.collect(module, info.diagnosticReporter)
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) = Unit
}

internal class SourcelessDiagnosticsChecker(testServices: TestServices) : AfterAnalysisChecker(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(LokkiDirectives)

    override val additionalServices: List<ServiceRegistrationData>
        get() = listOf(service(::SourcelessDiagnostics))

    override fun check(thereWereFailures: Boolean) {
        val expected = testServices.moduleStructure.originalTestDataFiles.first().withExtension("sourceless.txt")
        if (LokkiDirectives.CHECK_SOURCELESS_DIAGNOSTICS !in testServices.moduleStructure.allDirectives) {
            testServices.assertions.assertFileDoesntExist(expected, LokkiDirectives.CHECK_SOURCELESS_DIAGNOSTICS)
            return
        }
        testServices.assertions.assertEqualsToFile(expected, testServices.sourcelessDiagnostics.dump())
    }
}
