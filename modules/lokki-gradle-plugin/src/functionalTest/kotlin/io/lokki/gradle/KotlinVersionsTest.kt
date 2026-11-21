package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinVersionsTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun testMocks(kotlin: String) {
        val project = project(kotlin)
        project.write("src/test/kotlin/sample/GarageTest.kt", MOCKS_TEST)

        val result = project.build("test", "--info")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertContains(result.output, "class Car\$LokkiMock\$08768170\$GarageTestKt : Car, GeneratedMock")
        assertEquals(MOCKS_TEST_COUNT, project.testCount("sample.GarageTest"))
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun testInterceptionPoints(kotlin: String) {
        val project = project(kotlin)
        project.write("src/test/kotlin/sample/ClockTest.kt", INTERCEPTION_TEST)

        if (kotlin.hasInterceptionPoints) {
            val result = project.build("test")

            assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
            assertEquals(2, project.testCount("sample.ClockTest"))
        } else {
            val failure = project.buildAndFail("test")

            val errors = failure.output.lines().filter { it.startsWith("e: ") && "ClockTest.kt" in it }
            val noPoints = "has no interception points: lokki generates them on Kotlin $INTERCEPTION_POINTS_KOTLIN or newer"
            assertTrue(errors.any { "Calls of 'sample.Clock' cannot be intercepted: it $noPoints." in it }, failure.output)
            assertTrue(errors.any { "Calls of 'now' cannot be intercepted: it $noPoints." in it }, failure.output)
            assertTrue(errors.any { "A call of 'zone' cannot be intercepted: 'sample.Clock' $noPoints." in it }, failure.output)
        }
    }

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun testDiagnosticOfIrPass(kotlin: String) {
        val project = project(kotlin)
        project.write(
            "src/test/kotlin/sample/BoltTest.kt",
            """
            @file:Suppress("LOKKI_TYPE_NOT_MOCKABLE")

            package sample

            import io.lokki.mock

            fun bolt(): Bolt = mock<Bolt>()
            """,
        )

        val failure = project.buildAndFail("compileTestKotlin")

        val errors = failure.output.lines().filter { it.startsWith("e: ") }
        assertTrue(errors.any { "BoltTest.kt:7:20 'sample.Bolt' cannot be mocked: it is a final class." in it }, failure.output)
    }

    private fun project(kotlin: String): TestProject {
        val project = TestProject(projectDir, GradleUnderTest.CURRENT)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$kotlin"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            lokki {
                mockable("sample.Engine", "sample.Clock", "sample.now")
                debug = true
            }

            testing {
                suites {
                    named<JvmTestSuite>("test") {
                        useKotlinTest("$kotlin")
                    }
                }
            }
            """,
        )
        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("interface Car", "fun drive(speed: Int): String", "suspend fun park(): Boolean"),
        )
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))
        project.write(
            "src/main/kotlin/sample/Radio.kt",
            declaration("open class Radio(val station: String)", "open fun play(): String = station"),
        )
        project.write("src/main/kotlin/sample/Bolt.kt", "package sample\n\nclass Bolt\n")
        project.write(
            "src/main/kotlin/sample/Clock.kt",
            "package sample\n\nobject Clock {\n    fun zone(): String = \"UTC\"\n}\n\nfun now(): Long = 1\n",
        )
        return project
    }

    private companion object {
        @JvmStatic
        fun testedKotlin(): List<String> = TESTED_KOTLIN
    }
}

private const val MOCKS_TEST_COUNT = 6
private const val MOCKS_TEST: String =
    """
    package sample

    import io.lokki.Mock
    import io.lokki.coEvery
    import io.lokki.coVerify
    import io.lokki.every
    import io.lokki.junit5.LokkiExtension
    import io.lokki.mock
    import io.lokki.spy
    import io.lokki.verify
    import io.lokki.verifyOrder
    import org.junit.jupiter.api.extension.ExtendWith
    import kotlin.coroutines.Continuation
    import kotlin.coroutines.EmptyCoroutineContext
    import kotlin.coroutines.startCoroutine
    import kotlin.test.Test
    import kotlin.test.assertEquals

    abstract class BaseGarageTest {
        @Mock
        lateinit var inherited: Car
    }

    @ExtendWith(LokkiExtension::class)
    class GarageTest : BaseGarageTest() {
        @Mock
        lateinit var field: Car

        @Test
        fun stubsAndVerifiesWithMatchers() {
            val car = mock<Car>()
            every { car.drive(more(10)) } returns "fast"
            every { car.drive(less(10)) } answers { "slow at ${'$'}{firstArg<Int>()}" }

            assertEquals("fast", car.drive(60))
            assertEquals("slow at 5", car.drive(5))
            verify(exactly = 2) { car.drive(any()) }
            verifyOrder {
                car.drive(60)
                car.drive(5)
            }
        }

        @Test
        fun relaxedMockAnswersWithDefaults() {
            assertEquals("", mock<Car>(relaxed = true).drive(1))
        }

        @Test
        fun mocksClassOpenedInMain() {
            val engine = mock<Engine>()
            every { engine.start() } returns "mocked"

            assertEquals("mocked", engine.start())
        }

        @Test
        fun spyCallsTheOriginal() {
            val radio = spy(Radio("jazz"))

            assertEquals("jazz", radio.play())
            every { radio.play() } returns "news"
            assertEquals("news", radio.play())
        }

        @Test
        fun stubsSuspendMember() {
            val car = mock<Car>()
            coEvery { car.park() } returns true
            var parked: Result<Boolean>? = null

            suspend { car.park() }.startCoroutine(Continuation(EmptyCoroutineContext) { parked = it })

            assertEquals(Result.success(true), parked)
            suspend { coVerify { car.park() } }.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })
        }

        @Test
        fun initializesMockFields() {
            every { field.drive(any()) } returns "driven"
            every { inherited.drive(any()) } returns "inherited"

            assertEquals("driven", field.drive(1))
            assertEquals("inherited", inherited.drive(1))
        }
    }
    """
private const val INTERCEPTION_TEST: String =
    """
    package sample

    import io.lokki.every
    import io.lokki.lokkiScope
    import io.lokki.mockObject
    import io.lokki.mockStatic
    import kotlin.test.Test
    import kotlin.test.assertEquals

    class ClockTest {
        @Test
        fun stubsObject() {
            lokkiScope {
                mockObject(Clock)
                every { Clock.zone() } returns "MSK"
                assertEquals("MSK", Clock.zone())
            }
            assertEquals("UTC", Clock.zone())
        }

        @Test
        fun stubsFunction() {
            lokkiScope {
                mockStatic(::now)
                every { now() } returns 2
                assertEquals(2, now())
            }
            assertEquals(1, now())
        }
    }
    """
