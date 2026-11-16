package lokki.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

const val COMPILER_COMPAT_PROJECT = ":lokki-compiler-compat"

const val COMPILER_COMPAT_SERVICE = "META-INF/services/io.lokki.compiler.compat.CompilerCompat\$Factory"

const val COMPILER_ADAPTERS_MANIFEST = "modules/lokki-compiler-compat/adapters.properties"

private const val FACTORY_NAME = "CompilerCompatFactory"

private const val KOTLIN_COMPILER = "org.jetbrains.kotlin:kotlin-compiler"

private val qualifiedClassName = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

class CompilerAdapter internal constructor(val minVersion: String, val implementationClassName: String) {
    val projectPath: String
        get() = "$COMPILER_COMPAT_PROJECT:kotlin-$minVersion"

    val packageName: String
        get() = implementationClassName.substringBeforeLast('.')

    val factoryClassName: String
        get() = factoryClassNameOf(implementationClassName)

    override fun toString(): String = "$minVersion=$implementationClassName"
}

internal fun factoryClassNameOf(implementationClassName: String): String =
    "${implementationClassName.substringBeforeLast('.')}.$FACTORY_NAME"

internal fun isQualifiedClassName(name: String): Boolean = qualifiedClassName.matches(name)

internal fun File.writeGenerated(text: String) {
    parentFile.mkdirs()
    writeText(text)
}

internal fun parseCompilerAdapters(manifest: String): List<CompilerAdapter> {
    val adapters = manifest.lineSequence()
        .map(String::trim)
        .filterNot { it.isEmpty() || it.startsWith('#') }
        .map(::parseCompilerAdapter)
        .toList()
    if (adapters.isEmpty()) invalid("no adapters are declared")
    adapters.requireUnique("version") { it.minVersion }
    adapters.requireUnique("factory") { it.factoryClassName }
    return adapters.sortedBy { kotlinVersion(it.minVersion) }
}

private fun parseCompilerAdapter(entry: String): CompilerAdapter {
    val version = entry.substringBefore('=').trim()
    val implementation = entry.substringAfter('=', "").trim()
    if (runCatching { kotlinVersion(version) }.isFailure) invalid("'$version' is not a Kotlin version")
    if (!isQualifiedClassName(implementation)) invalid("'$implementation' of $version is not a qualified class name")
    if (implementation.substringAfterLast('.') == FACTORY_NAME) {
        invalid("'$implementation' of $version takes the name of the generated factory")
    }
    return CompilerAdapter(version, implementation)
}

private fun kotlinVersion(version: String): KotlinToolingVersion = KotlinToolingVersion(version)

private fun List<CompilerAdapter>.requireUnique(property: String, selector: (CompilerAdapter) -> String) {
    val repeated = groupBy(selector).entries.firstOrNull { it.value.size > 1 } ?: return
    invalid("$property ${repeated.key} is declared ${repeated.value.size} times")
}

private fun invalid(problem: String): Nothing = throw GradleException("$COMPILER_ADAPTERS_MANIFEST: $problem")

val Project.compilerAdapters: List<CompilerAdapter>
    get() {
        val root = isolated.rootProject.projectDirectory
        val adapters = parseCompilerAdapters(providers.fileContents(root.file(COMPILER_ADAPTERS_MANIFEST)).asText.get())
        adapters.forEach { adapter ->
            val module = root.dir("modules/lokki-compiler-compat/kotlin-${adapter.minVersion}")
            if (!module.asFile.isDirectory) invalid("adapter ${adapter.minVersion} has no module directory ${module.asFile}")
        }
        return adapters
    }

val Project.compilerAdapter: CompilerAdapter
    get() = compilerAdapters.firstOrNull { it.projectPath == path } ?: invalid("project $path has no adapter")

val Project.compilerApiBaseline: String
    get() = kotlinVersion(compilerAdapters.first().minVersion).run { "$major.$minor.$patch" }

val Project.testedKotlin: List<String>
    get() = providers.gradleProperty("lokki.kotlin.tested").get().split(',').also { tested ->
        val catalog = version("kotlin")
        check(catalog in tested) {
            "lokki.kotlin.tested=${tested.joinToString(",")} must include Kotlin $catalog of the version catalog"
        }
    }

val Project.latestTestedKotlin: String
    get() = testedKotlin.maxBy(::kotlinVersion)

fun Project.compileAgainstCompilerApi(compilerVersion: String) {
    dependencies.add("compileOnly", KOTLIN_COMPILER) {
        version { strictly(compilerVersion) }
    }
    val oldest = kotlinVersion(compilerApiBaseline)
    tasks.named<KotlinCompile>("compileKotlin") {
        compilerOptions.apiVersion.set(KotlinVersion.fromVersion("${oldest.major}.${oldest.minor}"))
    }
}

fun Project.checkLatestCompilerApi(): SourceSet {
    val latest = latestTestedKotlin
    val sourceSet = extensions.getByType<SourceSetContainer>().create("latestCompilerApi")
    val kotlin = extensions.getByType<KotlinJvmProjectExtension>()
    kotlin.sourceSets.named(sourceSet.name) {
        this.kotlin.source(kotlin.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).kotlin)
    }
    dependencies.add(sourceSet.compileOnlyConfigurationName, KOTLIN_COMPILER) {
        version { strictly(latest) }
    }
    val compile = tasks.named<KotlinCompile>(sourceSet.getCompileTaskName("kotlin")) {
        description = "Compiles the main sources against the compiler API of Kotlin $latest, the latest tested release"
    }
    tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(compile) }
    return sourceSet
}
