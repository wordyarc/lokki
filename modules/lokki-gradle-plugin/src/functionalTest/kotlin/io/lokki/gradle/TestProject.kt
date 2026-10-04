package io.lokki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.net.URLClassLoader

val KOTLIN_VERSION: String = System.getProperty("lokki.test.kotlinVersion")
val LOKKI_VERSION: String = System.getProperty("lokki.test.lokkiVersion")

enum class GradleUnderTest(val version: String?, val javaHome: String?) {
    CURRENT(null, null),
    MINIMAL(System.getProperty("lokki.test.minGradleVersion"), System.getProperty("lokki.test.minGradleJavaHome")),
}

class TestProject(
    private val dir: File,
    private val gradle: GradleUnderTest,
    subprojects: List<String> = emptyList(),
) {
    private val buildReports = dir.resolve("build/reports/kotlin-build")

    init {
        val repositories = System.getProperty("lokki.test.repositories").split(File.pathSeparator)
            .joinToString(separator = "; ", postfix = "; mavenCentral()") { "maven(\"${File(it).toURI()}\")" }
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
    }

    fun write(path: String, content: String) {
        dir.resolve(path).apply { parentFile.mkdirs() }.writeText(content.trimIndent())
    }

    fun build(vararg arguments: String): BuildResult {
        buildReports.deleteRecursively()
        val runner = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(*arguments, "--stacktrace")
            .forwardOutput()
        gradle.version?.let(runner::withGradleVersion)
        return runner.build()
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

    fun mockedMethods(sourceSet: String, className: String, project: String = "."): Set<String> {
        val classes = listOf(sourceSet, "main").map { dir.resolve("$project/build/classes/kotlin/$it").toURI().toURL() }
        return URLClassLoader(classes.toTypedArray()).use { loader ->
            val generated = loader.loadClass("io.lokki.internal.GeneratedMock").declaredMethods.map { it.name }
            loader.loadClass(className).declaredMethods.filterNot { it.name in generated }.mapTo(HashSet()) { method ->
                "${method.name}(${method.parameterTypes.joinToString { it.name }})"
            }
        }
    }
}
