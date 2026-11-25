package io.lokki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions
import java.io.File
import java.net.URLClassLoader

val KOTLIN_VERSION: String = System.getProperty("lokki.test.kotlinVersion")
val MIN_KOTLIN_VERSION: String = System.getProperty("lokki.test.minKotlinVersion")
val LATEST_TESTED_KOTLIN: String = System.getProperty("lokki.test.latestTestedKotlin")
val TESTED_KOTLIN: List<String> = System.getProperty("lokki.test.testedKotlin").split(',')
val LOKKI_GROUP: String = System.getProperty("lokki.test.lokkiGroup")
val LOKKI_PLUGIN_ID: String = System.getProperty("lokki.test.pluginId")
val LOKKI_VERSION: String = System.getProperty("lokki.test.lokkiVersion")
val COMPOSE_RUNTIME_VERSION: String = System.getProperty("lokki.test.composeRuntimeVersion")

const val INTERCEPTION_POINTS_KOTLIN: String = "2.4.20"

val String.hasInterceptionPoints: Boolean
    get() {
        val (major, minor, patch) = substringBefore('-').split('.').map(String::toInt)
        val (firstMajor, firstMinor, firstPatch) = INTERCEPTION_POINTS_KOTLIN.split('.').map(String::toInt)
        return KotlinVersion(major, minor, patch) >= KotlinVersion(firstMajor, firstMinor, firstPatch)
    }

fun assumeInterceptionPoints() {
    Assumptions.assumeTrue(KOTLIN_VERSION.hasInterceptionPoints) {
        "Kotlin $KOTLIN_VERSION generates no interception points"
    }
}

enum class GradleUnderTest(val version: String?, val javaHome: String?, val agpVersion: String) {
    CURRENT(null, null, System.getProperty("lokki.test.agpVersion")),
    MINIMAL(
        System.getProperty("lokki.test.minGradleVersion"),
        System.getProperty("lokki.test.minGradleJavaHome"),
        System.getProperty("lokki.test.minAgpVersion"),
    ),
}

private val SELECTED_GRADLE: GradleUnderTest? = System.getProperty("lokki.test.gradle")?.let { selected ->
    GradleUnderTest.entries.find { it.name.equals(selected, ignoreCase = true) }
        ?: error("lokki.test.gradle=$selected: expected 'current' or 'minimal'")
}

class TestProject(
    private val dir: File,
    private val gradle: GradleUnderTest,
    subprojects: List<String> = emptyList(),
    android: Boolean = false,
    google: Boolean = android,
) {
    private val buildReports = dir.resolve("build/reports/kotlin-build")

    init {
        Assumptions.assumeTrue(SELECTED_GRADLE == null || gradle == SELECTED_GRADLE) {
            "lokki.test.gradle selects the tests of ${SELECTED_GRADLE?.name?.lowercase()} Gradle"
        }
        val repositories = System.getProperty("lokki.test.repositories").split(File.pathSeparator)
            .map { "maven(\"${File(it).toURI()}\")" }
            .plus(listOfNotNull("google()".takeIf { google }, "mavenCentral()"))
            .joinToString("; ")
        val settings = listOf(
            "pluginManagement { repositories { $repositories } }",
            "dependencyResolutionManagement { repositories { $repositories } }",
            "rootProject.name = \"sample\"",
        ) + subprojects.map { "include(\"$it\")" }
        write("settings.gradle.kts", settings.joinToString("\n"))
        write(
            "gradle.properties",
            listOfNotNull(
                "org.gradle.configuration-cache=true",
                "kotlin.build.report.output=file",
                gradle.javaHome?.let { "org.gradle.java.home=$it" },
            ).joinToString("\n"),
        )
        if (android) {
            val sdk = File(System.getProperty("lokki.test.androidSdk"))
            check(sdk.isDirectory) { "Android SDK not found at $sdk: set ANDROID_HOME" }
            write("local.properties", "sdk.dir=${sdk.path}")
        }
    }

    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content.trimIndent())
    }

    fun build(vararg arguments: String): BuildResult = runner(arguments).build()

    fun buildAndFail(vararg arguments: String): BuildResult = runner(arguments).buildAndFail()

    private fun runner(arguments: Array<out String>): GradleRunner {
        buildReports.deleteRecursively()
        val runner = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(*arguments, "--stacktrace")
            .forwardOutput()
        gradle.version?.let(runner::withGradleVersion)
        return runner
    }

    fun testCount(className: String): Int {
        val report = dir.resolve("build/test-results/test/TEST-$className.xml").readText()
        return Regex("""<testsuite [^>]*tests="(\d+)"[^>]*failures="0" errors="0"""").find(report)!!.groupValues[1].toInt()
    }

    fun compiledSources(task: String): Set<String> {
        val sources = HashSet<String>()
        var inTask = false
        var inIteration = false
        for (line in buildReports.listFiles()!!.single().readLines()) {
            val text = line.trim()
            val source = text.substringBefore(" <- ")
            when {
                text.startsWith("Compilation log for task ") -> {
                    inTask = text == "Compilation log for task '$task':"
                    inIteration = false
                }
                !inTask -> continue
                text == "Compile iteration:" -> inIteration = true
                inIteration && source.endsWith(".kt") -> sources += File(source).name
                else -> inIteration = false
            }
        }
        return sources
    }

    fun mockedMethods(sourceSet: String, className: String, project: String = "."): Set<String> =
        withClasses(sourceSet, project) { loader ->
            val generated = listOf(loader.loadClass("io.lokki.internal.GeneratedMock"), Any::class.java)
                .flatMap { it.declaredMethods.asList() }
                .map { it.name }
            loader.loadClass(className).declaredMethods
                .filterNot { it.name in generated || it.isSynthetic }
                .mapTo(HashSet()) { method -> "${method.name}(${method.parameterTypes.joinToString { it.name }})" }
        }

    fun syntheticMethods(sourceSet: String, className: String, project: String = "."): Set<String> =
        withClasses(sourceSet, project) { loader ->
            loader.loadClass(className).declaredMethods
                .filter { it.isSynthetic }
                .mapTo(HashSet()) { method -> "${method.name}(${method.parameterTypes.joinToString { it.name }})" }
        }

    fun interfaces(sourceSet: String, className: String, project: String = "."): Set<String> =
        withClasses(sourceSet, project) { loader -> loader.loadClass(className).interfaces.mapTo(HashSet()) { it.name } }

    private fun <T> withClasses(sourceSet: String, project: String, block: (ClassLoader) -> T): T {
        val classes = listOf(sourceSet, "main").map { dir.resolve("$project/build/classes/kotlin/$it").toURI().toURL() }
        return URLClassLoader(classes.toTypedArray()).use(block)
    }
}
