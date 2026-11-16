package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

const val COMPILER_PLUGIN_ID = "io.lokki.compiler"

const val MOCKABLE_OPTION = "mockable"

const val DEBUG_OPTION = "debug"

const val LOKKI_ABI_VERSION = 1

val LOKKI_ABI_FLAGS = mapOf(
    "FLAG_RELAXED" to 1,
    "FLAG_RELAX_UNIT_FUN" to 2,
    "FLAG_SPY" to 4,
    "FLAG_DELEGATE" to 8,
    "FLAG_OBJECT" to 16,
    "FLAG_STATIC" to 32,
    "METHOD_RETURN_TYPE_NULLABLE" to 1,
    "METHOD_RETURNS_UNIT" to 2,
    "METHOD_RETURNS_NOTHING" to 4,
    "METHOD_SUSPEND" to 8,
    "METHOD_FN_CALL" to 16,
)

private val withoutPublicApi = setOf("compiler-tests", "lokki-compiler-plugin")

internal val Project.abiCheckEnabled: Boolean
    get() = name !in withoutPublicApi && !path.startsWith(COMPILER_COMPAT_PROJECT)

val Project.functionalTestRepository: Provider<Directory>
    get() = layout.buildDirectory.dir("functional-test-repository")
