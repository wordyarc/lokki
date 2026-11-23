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
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
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
            project.mockedMethods("testFixtures", "sample.Car\$LokkiMock\$08768170\$FixturesKt"),
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
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
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
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
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

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testMockableMain(gradle: GradleUnderTest) {
        val project = mockableProject(gradle, "\"sample.**\"")
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))
        project.write(
            "src/main/kotlin/sample/Registry.kt",
            """
            package sample

            object Registry {
                val engine = Engine()
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/EngineTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class EngineTest {
                @Test
                fun stubsFinalClassOfMain() {
                    val engine = mock<Engine>()
                    every { engine.start() } returns "mocked"
                    assertEquals("mocked", engine.start())
                    assertEquals("started", Registry.engine.start())
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertFalse(PUBLICATION_WARNING in result.output, result.output)
        assertTrue(project.dependsOnLokki("kotlinCompilerPluginClasspathMain"))
        assertFalse(project.dependsOnLokki("compileClasspath"))
        assertFalse(project.dependsOnLokki("runtimeClasspath"))
        val mainClasses = projectDir.resolve("build/classes/kotlin/main").walk().filter { it.extension == "class" }.toList()
        val generated = if (KOTLIN_VERSION.hasInterceptionPoints) {
            setOf(
                "Registry\$LokkiRedirect.class",
                "Engine\$LokkiConstructors.class",
                "Engine\$LokkiConstructorsHolder.class",
            )
        } else {
            emptySet()
        }
        assertEquals(setOf("Engine.class", "Registry.class") + generated, mainClasses.mapTo(HashSet()) { it.name })
        for (file in mainClasses) {
            assertFalse("io/lokki" in file.readText(Charsets.ISO_8859_1), file.name)
        }
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testInvalidMockablePattern(gradle: GradleUnderTest) {
        val project = mockableProject(gradle, "\"sample.*\"")
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))

        val result = project.buildAndFail("compileKotlin")

        assertTrue("Invalid mockable pattern 'sample.*'" in result.output, result.output)
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testPublishedMockableMain(gradle: GradleUnderTest) {
        val project = mockableProject(gradle, "\"sample.**\"", "`maven-publish`")
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))

        val suppressed = project.build("compileKotlin", "-Plokki.mockablePublication.nowarn=true")

        assertFalse(PUBLICATION_WARNING in suppressed.output, suppressed.output)

        val warned = project.build("compileKotlin")

        assertTrue(
            "lokki { mockable(...) } opens classes and adds interception points to objects and top-level functions " +
                "compiled by :compileKotlin, $PUBLICATION_WARNING" in warned.output,
            warned.output,
        )
    }

    private fun mockableProject(gradle: GradleUnderTest, patterns: String, vararg plugins: String): TestProject =
        TestProject(projectDir, gradle).apply {
            write(
                "build.gradle.kts",
                """
                plugins {
                    kotlin("jvm") version "$KOTLIN_VERSION"
                    id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
                    ${plugins.joinToString("; ")}
                }

                lokki {
                    mockable($patterns)
                }

                testing {
                    suites {
                        named<JvmTestSuite>("test") {
                            useKotlinTest("$KOTLIN_VERSION")
                        }
                    }
                }
                """,
            )
        }

    private fun TestProject.dependsOnLokki(configuration: String): Boolean =
        "$LOKKI_GROUP:" in build("dependencies", "--configuration", configuration).output
}

private const val PUBLICATION_WARNING = "and the project applies maven-publish: its published artifacts contain this bytecode."
