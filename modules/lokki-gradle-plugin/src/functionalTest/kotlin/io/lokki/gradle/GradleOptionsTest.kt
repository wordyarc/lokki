package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GradleOptionsTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testVersionlessLokkiDependencies(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            dependencies {
                implementation("$LOKKI_GROUP:lokki-core")
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
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.Mock
            import io.lokki.every
            import io.lokki.junit5.LokkiExtension
            import org.junit.jupiter.api.extension.ExtendWith
            import kotlin.test.Test
            import kotlin.test.assertEquals

            @ExtendWith(LokkiExtension::class)
            class CarTest {
                @Mock
                lateinit var car: Car

                @Test
                fun initializesMockFields() {
                    every { car.drive() } returns "driven"
                    assertEquals("driven", car.drive())
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        val main = project.build("dependencies", "--configuration", "compileClasspath").output
        assertContains(main, "$LOKKI_GROUP:lokki-core -> $LOKKI_VERSION")
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testVersionlessBddArtifact(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            dependencies {
                testImplementation("$LOKKI_GROUP:lokki-bdd")
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
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.bdd.given
            import io.lokki.bdd.then
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarTest {
                @Test
                fun stubsAndVerifies() {
                    val car = mock<Car>()
                    given { car.drive() } returns "driven"

                    assertEquals("driven", car.drive())
                    then(exactly = 1) { car.drive() }
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        val classpath = project.build("dependencies", "--configuration", "testCompileClasspath").output
        assertContains(classpath, "$LOKKI_GROUP:lokki-bdd -> $LOKKI_VERSION")
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testOlderKotlin(gradle: GradleUnderTest) {
        val project = kotlinVersionProject(gradle, OLDER_KOTLIN)

        val result = project.buildAndFail("help")

        assertContains(
            result.output,
            "lokki $LOKKI_VERSION requires Kotlin $MIN_KOTLIN_VERSION or newer, but the build uses Kotlin " +
                "$OLDER_KOTLIN: the lokki compiler plugin has no adapter to its compiler API.",
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewerKotlin(gradle: GradleUnderTest) {
        val project = reportedKotlinVersionProject(gradle, NEWER_KOTLIN)

        val suppressed = project.build("help", "-Plokki.kotlinCompatibility.nowarn=true")

        assertFalse(NEWER_KOTLIN_WARNING in suppressed.output, suppressed.output)

        val warned = project.build("help")

        assertContains(
            warned.output,
            "lokki $LOKKI_VERSION is tested with Kotlin up to $LATEST_TESTED_KOTLIN, but the build uses Kotlin " +
                "$NEWER_KOTLIN: if the lokki compiler plugin fails, use Kotlin $LATEST_TESTED_KOTLIN or a newer " +
                "lokki. $NEWER_KOTLIN_WARNING",
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testApplyToMain(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            lokki {
                applyToMain = true
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
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write(
            "src/main/kotlin/sample/Cars.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.mock

            fun drivingCar(): Car = mock<Car> { every { drive() } returns "driven" }
            """,
        )
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarTest {
                @Test
                fun usesMockOfMain() {
                    assertEquals("driven", drivingCar().drive())
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("drive()"), project.mockedMethods("main", "sample.Car\$LokkiMock\$08768170\$CarsKt"))
        assertContains(project.build("dependencies", "--configuration", "compileClasspath").output, "$LOKKI_GROUP:lokki-core")
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testRecordStackTraces(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            lokki {
                if (providers.gradleProperty("disabledInScript").isPresent) recordStackTraces = false
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
        project.write(
            "src/test/kotlin/sample/StackTracesTest.kt",
            """
            package sample

            import java.io.File
            import kotlin.test.Test

            class StackTracesTest {
                @Test
                fun writesProperty() {
                    File("build/stackTraces.txt").writeText("${'$'}{System.getProperty("lokki.recordStackTraces")}")
                }
            }
            """,
        )
        val property = projectDir.resolve("build/stackTraces.txt")

        project.build("test")

        assertEquals("null", property.readText())

        project.build("test", "-Plokki.recordStackTraces=true")

        assertEquals("true", property.readText())

        val disabled = project.build("test", "-Plokki.recordStackTraces=true", "-PdisabledInScript")

        assertEquals(TaskOutcome.SUCCESS, disabled.task(":test")?.outcome)
        assertEquals("null", property.readText())
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testGroovyBuildScript(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle",
            """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '$KOTLIN_VERSION'
                id '$LOKKI_PLUGIN_ID' version '$LOKKI_VERSION'
            }

            lokki {
                mockable 'sample.**', 'sample.Engine'
                restricted 'sample.Legacy'
                testCompilations 'integrationTest'
                recordStackTraces = true
            }
            """,
        )

        val main = project.build("dependencies", "--configuration", "kotlinCompilerPluginClasspathMain").output

        assertContains(main, "$LOKKI_GROUP:lokki-compiler-plugin")
        assertFalse("$LOKKI_GROUP:" in project.build("dependencies", "--configuration", "compileClasspath").output)
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testSettingChangedAfterItWasRead(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            afterEvaluate {
                lokki {
                    if (providers.gradleProperty("lateMockable").isPresent) mockable("sample.**")
                    if (providers.gradleProperty("lateApplyToMain").isPresent) applyToMain = true
                }
            }
            """,
        )

        assertContains(
            project.buildAndFail("help", "-PlateMockable").output,
            "The value for extension 'lokki' property 'mockable' is final and cannot be changed any further.",
        )
        assertContains(
            project.buildAndFail("help", "-PlateApplyToMain").output,
            "The value for extension 'lokki' property 'applyToMain' is final and cannot be changed any further.",
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testDebug(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            """,
        )
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/test/kotlin/sample/CarTest.kt", "package sample\n\nfun car(): Car = io.lokki.mock<Car>()\n")
        project.write("src/test/kotlin/sample/Unrelated.kt", "package sample\n\nfun unrelated() = 1\n")

        val debug = project.build("compileTestKotlin", "--info", "-Plokki.debug=true").output

        assertContains(debug, "CarTest.kt:")
        assertContains(debug, "class Car\$LokkiMock\$08768170\$CarTestKt : Car, GeneratedMock")
        assertFalse("Unrelated.kt:" in debug, debug)

        val plain = project.build("compileTestKotlin", "--info", "-Plokki.debug=false").output

        assertFalse("Car\$LokkiMock\$08768170\$CarTestKt" in plain, plain)
    }

    private fun reportedKotlinVersionProject(gradle: GradleUnderTest, kotlin: String): TestProject =
        TestProject(projectDir, gradle).apply {
            write(
                "build.gradle.kts",
                """
                import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin

                plugins {
                    kotlin("jvm") version "$KOTLIN_VERSION" apply false
                    id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
                }

                class ReportedKotlin : KotlinBasePlugin {
                    override val pluginVersion: String = "$kotlin"

                    override fun apply(project: Project) = Unit
                }

                apply<ReportedKotlin>()
                """,
            )
        }

    private fun kotlinVersionProject(gradle: GradleUnderTest, kotlin: String): TestProject =
        TestProject(projectDir, gradle).apply {
            write(
                "build.gradle.kts",
                """
                plugins {
                    kotlin("jvm") version "$kotlin"
                    id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
                }
                """,
            )
        }
}

private const val OLDER_KOTLIN = "2.3.21"
private const val NEWER_KOTLIN = "9.0.0"
private const val NEWER_KOTLIN_WARNING =
    "To disable this warning, set lokki.kotlinCompatibility.nowarn=true in gradle.properties."
