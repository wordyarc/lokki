import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.lokki)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    compilerOptions {
        freeCompilerArgs.add("-Xverify-ir=error")
    }
}

lokki {
    mockable("io.lokki.it.mockable.**")
    interceptable(
        "io.lokki.it.mockable.Clock",
        "io.lokki.it.mockable.Ticket",
        "io.lokki.it.mockable.today",
        "io.lokki.it.mockable.stamp",
        "io.lokki.it.conformance.**",
    )
}

val interceptionPoints = libs.versions.kotlin.get().substringBefore('-').split('.').map(String::toInt)
    .let { (major, minor, patch) -> KotlinVersion(major, minor, patch).isAtLeast(2, 4, 20) }

val validationTests = "src/validation/kotlin"

sourceSets.test {
    if (interceptionPoints) kotlin.srcDir("src/interceptionTest/kotlin")
    kotlin.srcDir(validationTests)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useKotlinTest(libs.versions.kotlin)

            dependencies {
                implementation(libs.junit.platform.testkit)
                implementation(libs.junit.jupiter.params)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.spring.aop)
                implementation(libs.spring.context)
                implementation(libs.spring.test)
                implementation("io.github.wordyarc.lokki:lokki-spring")
                implementation(libs.hibernate.validator)
                implementation(libs.jackson.databind)
                implementation(libs.gson)
            }
        }

        register<JvmTestSuite>("javaParametersTest") {
            useKotlinTest(libs.versions.kotlin)

            sources {
                kotlin.srcDir(validationTests)
            }

            dependencies {
                implementation(libs.spring.aop)
                implementation(libs.spring.context)
                implementation(libs.hibernate.validator)
            }
        }
    }
}

tasks.named<KotlinCompile>("compileJavaParametersTestKotlin") {
    compilerOptions.javaParameters = true
}

tasks.check {
    dependsOn(testing.suites.named("javaParametersTest"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        excludeTags("sample")
    }
}

val slowJupiterIndependentTests = listOf(
    "io.lokki.it.conformance.TimeoutTest",
    "io.lokki.it.conformance.ParallelTest",
)

jupiterTest("testLatestJupiter5", libs.versions.junit.jupiter)
jupiterTest("testLatestJupiter6", libs.versions.jupiter6, libs.versions.spring.latest)
jupiterTest("testUnsupportedJupiter", libs.versions.jupiter.unsupported) {
    filter.includeTestsMatching("io.lokki.it.junit5.JupiterVersionTest")
}
jupiterTest("testUnsupportedSpring", libs.versions.jupiter.min, libs.versions.spring.unsupported) {
    filter.includeTestsMatching("io.lokki.it.spring.SpringVersionTest")
}

fun jupiterTest(
    name: String,
    jupiter: Provider<String>,
    spring: Provider<String>? = null,
    configure: Test.() -> Unit = {},
) {
    val runtimeClasspath = configurations.resolvable("${name}RuntimeClasspath") {
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
        attributes.addAllLater(configurations.testRuntimeClasspath.get().attributes)
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "org.junit", "org.junit.jupiter" -> useVersion(jupiter.get())
                "org.junit.platform" -> useVersion(jupiter.get().replace(Regex("^5\\."), "1."))
                "org.springframework" -> spring?.let { useVersion(it.get()) }
            }
        }
    }
    val test = tasks.register<Test>(name) {
        description = "Runs the tests on JUnit Jupiter ${jupiter.get()}."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + runtimeClasspath.get()
        slowJupiterIndependentTests.forEach(filter::excludeTestsMatching)
        configure()
    }
    tasks.check {
        dependsOn(test)
    }
}
