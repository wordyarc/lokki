package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompilationApplicabilityTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testTestFixturesAndTestSuites(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                `java-test-fixtures`
                id("io.lokki") version "$LOKKI_VERSION"
            }

            testing {
                suites {
                    withType<JvmTestSuite>().configureEach {
                        useKotlinTest("$KOTLIN_VERSION")
                    }
                    register<JvmTestSuite>("integrationTest") {
                        dependencies {
                            implementation(project())
                        }
                    }
                }
            }
            """,
        )
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write(
            "src/testFixtures/kotlin/sample/Fixtures.kt",
            """
            package sample

            import io.lokki.mock

            fun fixtureCar(): Car = mock<Car>()
            """,
        )
        project.write("src/test/kotlin/sample/FixtureTest.kt", mockTest("FixtureTest", "Car", "fixtureCar()"))
        project.write("src/integrationTest/kotlin/sample/CarIntegrationTest.kt", mockTest("CarIntegrationTest", "Car"))

        val result = project.build("test", "integrationTest")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":integrationTest")?.outcome)
        assertEquals(
            setOf("drive()"),
            project.declaredMethods("testFixtures", "sample.Car\$LokkiMock\$08768170\$FixturesKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testMainAndFeatureVariant(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("io.lokki") version "$LOKKI_VERSION"
            }

            java {
                registerFeature("extra") {
                    usingSourceSet(sourceSets.create("extra"))
                }
            }
            """,
        )

        assertTrue(project.dependsOnLokki("testCompileClasspath"))
        assertTrue(project.dependsOnLokki("kotlinCompilerPluginClasspathTest"))
        val unaffected = listOf(
            "compileClasspath",
            "runtimeClasspath",
            "kotlinCompilerPluginClasspathMain",
            "extraCompileClasspath",
            "extraRuntimeClasspath",
            "kotlinCompilerPluginClasspathExtra",
        )
        for (configuration in unaffected) {
            assertFalse(project.dependsOnLokki(configuration), configuration)
        }
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testMultiplatformJvmTest(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("multiplatform") version "$KOTLIN_VERSION"
                id("io.lokki") version "$LOKKI_VERSION"
            }

            kotlin {
                jvm()

                sourceSets {
                    jvmTest.dependencies {
                        implementation(kotlin("test"))
                        runtimeOnly("org.junit.platform:junit-platform-launcher")
                    }
                }
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
            """,
        )
        project.write("src/jvmMain/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/jvmMain/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write("src/jvmTest/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))

        val result = project.build("jvmTest")

        assertEquals(TaskOutcome.SUCCESS, result.task(":jvmTest")?.outcome)
        assertFalse(project.dependsOnLokki("jvmCompileClasspath"))
        assertFalse(project.dependsOnLokki("kotlinCompilerPluginClasspathJvmMain"))
    }

    private fun TestProject.dependsOnLokki(configuration: String): Boolean =
        "io.lokki:" in build("dependencies", "--configuration", configuration).output
}
