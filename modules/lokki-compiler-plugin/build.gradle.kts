@file:OptIn(ExperimentalWasmDsl::class)

import lokki.buildlogic.ClasspathSystemProperty
import lokki.buildlogic.LocationSystemProperty
import lokki.buildlogic.pluginId
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8EnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8Plugin
import org.jetbrains.kotlin.gradle.targets.wasm.d8.D8SetupTask

plugins {
    id("lokki.kotlin-jvm")
    `java-test-fixtures`
    id("com.github.gmazzo.buildconfig")
}

// Downloads the V8 shell that runs the JS box tests.
apply<D8Plugin>()
val d8 = the<D8EnvSpec>()

val testData = layout.projectDirectory.dir("testData")

val compilerTestLibraries = configurations.dependencyScope("compilerTestLibraries")
val compilerTestLibrariesClasspath = configurations.resolvable("compilerTestLibrariesClasspath") {
    extendsFrom(compilerTestLibraries)
    isTransitive = false
}

val annotationsRuntime = configurations.dependencyScope("annotationsRuntime")
val annotationsJvmRuntimeClasspath = configurations.resolvable("annotationsJvmRuntimeClasspath") {
    extendsFrom(annotationsRuntime)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}
val annotationsJsRuntimeClasspath = configurations.resolvable("annotationsJsRuntimeClasspath") {
    extendsFrom(annotationsRuntime)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(KotlinUsages.KOTLIN_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
    }
}

dependencies {
    compileOnly(libs.kotlin.compiler)

    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.test.framework)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesRuntimeOnly(libs.junit)

    compilerTestLibraries(libs.bundles.kotlin.compiler.test.libraries)
    annotationsRuntime(project(":lokki-plugin-annotations"))
}

buildConfig {
    packageName("org.jetbrains.kotlin.compiler.plugin.template")
    buildConfigField("KOTLIN_PLUGIN_ID", pluginId)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

val generateTests = tasks.register<JavaExec>("generateTests") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates a JUnit test for every file in testData"

    val output = layout.buildDirectory.dir("generated/tests")
    inputs.dir(testData)
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
        .withPropertyName("generatedTests")

    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass = "org.jetbrains.kotlin.compiler.plugin.template.GenerateTestsKt"
    // Generated tests address test data from the root directory: the Kotlin Compiler DevKit plugin resolves
    // these paths against the project root, and relative paths keep the sources the same in any checkout.
    workingDir = rootDir
    args(output.get().asFile.toRelativeString(rootDir), testData.asFile.toRelativeString(rootDir))
}

sourceSets.test {
    java.srcDir(generateTests)
}

tasks.test {
    inputs.dir(testData)
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    workingDir = rootDir
    systemProperty("idea.ignore.disabled.plugins", "true")
    jvmArgumentProviders.add(LocationSystemProperty("idea.home.path", provider { workingDir.path }))

    // The test framework looks up each of its libraries by a system property named after the artifact.
    val libraries = compilerTestLibrariesClasspath.get()
    libs.bundles.kotlin.compiler.test.libraries.get().forEach { library ->
        val artifact = libraries.incoming.artifactView {
            componentFilter { it is ModuleComponentIdentifier && it.module == library.name }
        }.files
        jvmArgumentProviders.add(ClasspathSystemProperty("org.jetbrains.kotlin.test.${library.name}", artifact))
    }

    jvmArgumentProviders.add(
        ClasspathSystemProperty("annotationsRuntime.jvm.classpath", files(annotationsJvmRuntimeClasspath))
    )
    jvmArgumentProviders.add(
        ClasspathSystemProperty("annotationsRuntime.js.classpath", files(annotationsJsRuntimeClasspath))
    )

    // Properties required to run JS tests from the internal test framework.
    dependsOn(tasks.withType<D8SetupTask>())
    jvmArgumentProviders.add(LocationSystemProperty("javascript.engine.path.V8", d8.executable))
    jvmArgumentProviders.add(ClasspathSystemProperty("javascript.engine.path.repl", files("repl.js")))
    jvmArgumentProviders.add(
        LocationSystemProperty(
            "kotlin.js.test.root.out.dir",
            layout.buildDirectory.dir("js-test-output").map { it.asFile.path },
        )
    )
}
