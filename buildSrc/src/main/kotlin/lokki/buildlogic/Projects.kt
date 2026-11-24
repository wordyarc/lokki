package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

internal const val REPOSITORY_PATH = "wordyarc/lokki"

const val REPOSITORY_URL = "https://github.com/$REPOSITORY_PATH"

const val COMPILER_PLUGIN_ID = "io.lokki.compiler"

internal val Project.kotlinModuleName: String
    get() = "io.lokki_$name"

const val MOCKABLE_OPTION = "mockable"

const val RESTRICTED_OPTION = "restricted"

const val DEBUG_OPTION = "debug"

const val LOKKI_ABI_VERSION = 6

const val FILE_REDIRECT_SUFFIX = "\$LokkiRedirect"

const val PRIVATE_HOOK_INFIX = "\$lokkiPrivate\$"

val LOKKI_ABI_FLAGS = mapOf(
    "FLAG_RELAXED" to 1,
    "FLAG_RELAX_UNIT_FUN" to 2,
    "FLAG_SPY" to 4,
    "FLAG_DELEGATE" to 8,
    "FLAG_OBJECT" to 16,
    "FLAG_STATIC" to 32,
    "FLAG_CONSTRUCTOR" to 64,
    "FLAG_RECORD_PRIVATE_CALLS" to 128,
    "METHOD_RETURN_TYPE_NULLABLE" to 1,
    "METHOD_RETURNS_UNIT" to 2,
    "METHOD_RETURNS_NOTHING" to 4,
    "METHOD_SUSPEND" to 8,
    "METHOD_FN_CALL" to 16,
    "METHOD_NO_DEFAULT_FIELD" to 32,
    "METHOD_PRIVATE_CALL" to 64,
)

private val withoutPublicApi = setOf("compiler-tests", "lokki-compiler-plugin", "lokki-maven")

internal val Project.abiCheckEnabled: Boolean
    get() = name !in withoutPublicApi && !path.startsWith(COMPILER_COMPAT_PROJECT)

val Project.functionalTestRepository: Provider<Directory>
    get() = layout.buildDirectory.dir("functional-test-repository")
