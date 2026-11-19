package io.lokki.compiler.test.runners

import io.lokki.compiler.test.services.LookupsChecker
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives
import org.jetbrains.kotlin.test.services.TestPhase

open class AbstractJvmLookupDumpTest : AbstractJvmDiagnosticTest() {
    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)

        defaultDirectives {
            TestPhaseDirectives.RUN_PIPELINE_TILL with TestPhase.entries.last()
        }

        useAfterAnalysisCheckers(::LookupsChecker)
    }
}
