package io.lokki.compiler.test.runners

import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.DependencyKind

open class AbstractJvmBinaryDependencyDiagnosticTest : AbstractJvmDiagnosticTest() {
    override fun configure(builder: TestConfigurationBuilder) = with(builder) {
        super.configure(builder)

        globalDefaults {
            dependencyKind = DependencyKind.Binary
        }
    }
}
