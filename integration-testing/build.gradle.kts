plugins {
    kotlin("jvm") version libs.versions.kotlin
    id("io.lokki")
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

lokki {
    mockable("io.lokki.it.mockable.**")
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useKotlinTest(libs.versions.kotlin)

            dependencies {
                implementation("io.lokki:lokki-junit5")
                implementation(libs.junit.platform.testkit)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        excludeTags("sample")
    }
}

jupiterTest("testLatestJupiter5", libs.versions.junit.jupiter)
jupiterTest("testLatestJupiter6", libs.versions.jupiter6)
jupiterTest("testUnsupportedJupiter", libs.versions.jupiter.unsupported) {
    filter.includeTestsMatching("io.lokki.it.junit5.JupiterVersionTest")
}

fun jupiterTest(name: String, jupiter: Provider<String>, configure: Test.() -> Unit = {}) {
    val runtimeClasspath = configurations.resolvable("${name}RuntimeClasspath") {
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
        attributes.addAllLater(configurations.testRuntimeClasspath.get().attributes)
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "org.junit", "org.junit.jupiter" -> useVersion(jupiter.get())
                "org.junit.platform" -> useVersion(jupiter.get().replace(Regex("^5\\."), "1."))
            }
        }
    }
    val test = tasks.register<Test>(name) {
        description = "Runs the tests on JUnit Jupiter ${jupiter.get()}."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + runtimeClasspath.get()
        configure()
    }
    tasks.check {
        dependsOn(test)
    }
}
