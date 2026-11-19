package io.lokki.compiler.test.runners

import io.lokki.compiler.test.services.IrSourcelessDiagnosticsHandler
import io.lokki.compiler.test.services.SourcelessDiagnosticsChecker
import io.lokki.compiler.test.services.configurePlugin
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.TestPhaseDirectives
import org.jetbrains.kotlin.test.runners.AbstractFirPhasedDiagnosticTest
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.TestPhase

open class AbstractJvmDiagnosticTest : AbstractFirPhasedDiagnosticTest(FirParser.LightTree) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
        return EnvironmentBasedStandardLibrariesPathProvider
    }

    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)

        defaultDirectives {
            +FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
            +ConfigurationDirectives.WITH_STDLIB
            +JvmEnvironmentConfigurationDirectives.FULL_JDK
            JvmEnvironmentConfigurationDirectives.JVM_TARGET with JvmTarget.JVM_17

            +CodegenTestDirectives.IGNORE_DEXING // Avoids loading R8 from the classpath.
        }

        forTestsMatching("*/diagnostics/backend/*") {
            defaultDirectives {
                TestPhaseDirectives.RUN_PIPELINE_TILL with TestPhase.entries.last()
            }
        }

        configurePlugin()
        configureIrHandlersStep {
            useHandlers(::IrSourcelessDiagnosticsHandler)
        }
        useAfterAnalysisCheckers(::SourcelessDiagnosticsChecker)
    }
}
