package io.lokki.compiler.test

import io.lokki.compiler.test.runners.AbstractJvmBinaryDependencyDiagnosticTest
import io.lokki.compiler.test.runners.AbstractJvmBoxTest
import io.lokki.compiler.test.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testsRoot = args[0], testDataRoot = args[1]) {
            testClass<AbstractJvmDiagnosticTest> {
                model("diagnostics", excludeDirs = listOf("binaryDependencies"))
            }

            testClass<AbstractJvmBinaryDependencyDiagnosticTest> {
                model("diagnostics/binaryDependencies")
            }

            testClass<AbstractJvmBoxTest> {
                model("box")
            }
        }
    }
}
