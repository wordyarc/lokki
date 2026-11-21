import lokki.buildlogic.COMPILER_COMPAT_PROJECT
import lokki.buildlogic.GenerateCompilerCompatProvider
import lokki.buildlogic.compileAgainstCompilerApi
import lokki.buildlogic.compilerAdapter
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("lokki.compiler-api")
}

val adapter = compilerAdapter

description = "Adapter of the lokki compiler plugin to the compiler API of Kotlin ${adapter.minVersion}"

compileAgainstCompilerApi(adapter.minVersion)

java {
    withSourcesJar()
}

dependencies {
    api(project(COMPILER_COMPAT_PROJECT))
}

val generateCompilerCompatProvider = tasks.register<GenerateCompilerCompatProvider>("generateCompilerCompatProvider") {
    minVersion = adapter.minVersion
    implementationClassName = adapter.implementationClassName
    sourceDirectory = layout.buildDirectory.dir("generated/compat/kotlin")
    resourceDirectory = layout.buildDirectory.dir("generated/compat/resources")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateCompilerCompatProvider.flatMap { it.sourceDirectory })
        resources.srcDir(generateCompilerCompatProvider.flatMap { it.resourceDirectory })
    }
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.moduleName = adapter.packageName
}
