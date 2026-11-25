package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals

class PowerAssertTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testAssertionsInStubAndVerifyBlocks(gradle: GradleUnderTest) {
        build(gradle, listOf(LOKKI_PLUGIN, POWER_ASSERT_PLUGIN))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testPowerAssertPluginAppliedFirst(gradle: GradleUnderTest) {
        build(gradle, listOf(POWER_ASSERT_PLUGIN, LOKKI_PLUGIN))
    }

    private fun build(gradle: GradleUnderTest, plugins: List<String>) {
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                ${plugins.joinToString("\n                ")}
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            powerAssert {
                functions = listOf("kotlin.assert", "kotlin.test.assertEquals", "kotlin.test.assertTrue")
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
            "src/main/kotlin/sample/Car.kt",
            """
            package sample

            interface Car {
                val name: String

                fun drive(speed: Int): String
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.mock
            import io.lokki.verify
            import kotlin.test.Test
            import kotlin.test.assertContains
            import kotlin.test.assertEquals
            import kotlin.test.assertFailsWith
            import kotlin.test.assertTrue

            class CarTest {
                @Test
                fun assertsInMatchersAndOnAnswers() {
                    val car = mock<Car>()
                    every { car.drive(match { assertTrue(it > 0); true }) } returns "ok"
                    every { car.name } returns "car"
                    assert(car.drive(5) == "ok")
                    assertEquals("car", car.name)
                    verify { car.drive(withArg { assertEquals(5, it) }) }
                }

                @Test
                fun reportsAnswerOfMockInDiagram() {
                    val car = mock<Car>()
                    every { car.drive(any()) } returns "slow"
                    val failure = assertFailsWith<AssertionError> { assert(car.drive(5) == "fast") }
                    assertContains(failure.message.orEmpty(), "slow")
                }

                @Test
                fun reportsMismatchOfVerifiedArgument() {
                    val car = mock<Car>()
                    every { car.drive(any()) } returns "ok"
                    car.drive(5)
                    val failure = assertFailsWith<AssertionError> {
                        verify { car.drive(withArg { assertEquals(6, it) }) }
                    }
                    assertContains(failure.message.orEmpty(), "Assertion errors in matchers")
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(3, project.testCount("sample.CarTest"))
    }
}

private val POWER_ASSERT_PLUGIN = "kotlin(\"plugin.power-assert\") version \"$KOTLIN_VERSION\""
