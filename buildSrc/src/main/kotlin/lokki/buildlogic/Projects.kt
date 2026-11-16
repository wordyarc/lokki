package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

const val COMPILER_PLUGIN_ID = "io.lokki.compiler"

const val MOCKABLE_OPTION = "mockable"

const val DEBUG_OPTION = "debug"

const val LOKKI_ABI_VERSION = 1

private val withoutPublicApi = setOf("compiler-tests", "lokki-compiler-plugin")

internal val Project.abiCheckEnabled: Boolean
    get() = name !in withoutPublicApi && !path.startsWith(COMPILER_COMPAT_PROJECT)

val Project.functionalTestRepository: Provider<Directory>
    get() = layout.buildDirectory.dir("functional-test-repository")
