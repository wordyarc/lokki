package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

const val COMPILER_PLUGIN_ID = "io.lokki.compiler"

const val MOCKABLE_OPTION = "mockable"

private val withoutPublicApi = setOf("compiler-tests", "lokki-compiler-plugin")

private val jvmRuntime = setOf("lokki-junit5")

internal val Project.abiCheckEnabled: Boolean
    get() = name !in withoutPublicApi

internal val Project.jvmTarget: String
    get() = version(if (name in jvmRuntime) "jvm-target-runtime" else "jvm-target-tooling")

val Project.functionalTestRepository: Provider<Directory>
    get() = layout.buildDirectory.dir("functional-test-repository")
