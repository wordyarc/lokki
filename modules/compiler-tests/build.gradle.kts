import lokki.buildlogic.ClasspathSystemProperty
import lokki.buildlogic.LocationSystemProperty
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("lokki.kotlin-jvm")
    `java-test-fixtures`
}

val testData = layout.projectDirectory.dir("src/test/data")

val compilerTestLibraries = configurations.dependencyScope("compilerTestLibraries")
val compilerTestLibrariesClasspath = configurations.resolvable("compilerTestLibrariesClasspath") {
    extendsFrom(compilerTestLibraries)
    isTransitive = false
}

val lokkiRuntime = configurations.dependencyScope("lokkiRuntime")
val lokkiRuntimeClasspath = configurations.resolvable("lokkiRuntimeClasspath") {
    extendsFrom(lokkiRuntime)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

val kotlinxCoroutines = configurations.dependencyScope("kotlinxCoroutines")
val kotlinxCoroutinesClasspath = configurations.resolvable("kotlinxCoroutinesClasspath") {
    extendsFrom(kotlinxCoroutines)
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

dependencies {
    testFixturesApi(project(":lokki-compiler-plugin"))
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.test.framework)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesRuntimeOnly(libs.junit)

    compilerTestLibraries(libs.bundles.kotlin.compiler.test.libraries)
    lokkiRuntime(project(":lokki-core"))
    kotlinxCoroutines(libs.kotlinx.coroutines.core)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

val generateTests = tasks.register<JavaExec>("generateTests") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Generates a JUnit test for every file in the test data"

    val output = layout.buildDirectory.dir("generated/tests")
    inputs.dir(testData)
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(output)
        .withPropertyName("generatedTests")

    classpath = sourceSets.testFixtures.get().runtimeClasspath
    mainClass = "io.lokki.compiler.test.GenerateTestsKt"
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

    val libraries = compilerTestLibrariesClasspath.get()
    libs.bundles.kotlin.compiler.test.libraries.get().forEach { library ->
        val artifact = libraries.incoming.artifactView {
            componentFilter { it is ModuleComponentIdentifier && it.module == library.name }
        }.files
        jvmArgumentProviders.add(ClasspathSystemProperty("org.jetbrains.kotlin.test.${library.name}", artifact))
        JAVAC_LIBRARY_PROPERTIES[library.name]?.let { jvmArgumentProviders.add(ClasspathSystemProperty(it, artifact)) }
    }

    jvmArgumentProviders.add(ClasspathSystemProperty("lokki.runtime.classpath", files(lokkiRuntimeClasspath)))
    jvmArgumentProviders.add(
        ClasspathSystemProperty("kotlinx.coroutines.classpath", files(kotlinxCoroutinesClasspath))
    )
}

val JAVAC_LIBRARY_PROPERTIES = mapOf(
    "kotlin-stdlib-jdk8" to "kotlin.full.stdlib.path",
    "kotlin-reflect" to "kotlin.reflect.jar.path",
    "kotlin-annotations-jvm" to "kotlin.mockJDK.annotations.path",
)
