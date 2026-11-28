package io.lokki.maven

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MavenTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest(name = "Kotlin {0}")
    @MethodSource("testedKotlin")
    fun testMocks(kotlin: String) {
        val project = TestProject(projectDir, kotlin = kotlin)
        project.pom()
        project.write("src/main/kotlin/sample/Garage.kt", GARAGE)
        project.write("src/test/kotlin/sample/GarageTest.kt", GARAGE_TEST)

        val result = project.build("test")

        assertContains(result.output, "Applied plugin: 'lokki'")
        assertEquals(GARAGE_TEST_COUNT, project.testCount("sample.GarageTest"))
    }

    @Test
    fun testMinimalMaven() {
        val project = TestProject(projectDir, MavenUnderTest.MINIMAL)
        project.pom()
        project.write("src/main/kotlin/sample/Garage.kt", GARAGE)
        project.write("src/test/kotlin/sample/GarageTest.kt", GARAGE_TEST)

        project.build("test")

        assertEquals(GARAGE_TEST_COUNT, project.testCount("sample.GarageTest"))
    }

    @Test
    fun testInterceptableInCompileExecution() {
        assumeInterceptionPoints()
        val project = TestProject(projectDir)
        project.pom(compileConfiguration = INTERCEPTABLE_OPTIONS)
        project.write("src/main/kotlin/sample/Clock.kt", CLOCK)
        project.write("src/test/kotlin/sample/ClockTest.kt", CLOCK_TEST)

        val result = project.build("test")

        assertEquals(3, project.testCount("sample.ClockTest"))
        val warnings = result.output.lines().filter { "lokki:interceptable adds interception points" in it }
        assertEquals(1, warnings.size, result.output)
        assertContains(
            warnings.single(),
            "[WARNING] lokki:interceptable adds interception points to objects, top-level functions and " +
                "constructor calls and opens classes compiled by the execution 'compile' of sample:sample, and the " +
                "artifacts of this project contain this bytecode. To disable this warning, set the property " +
                "lokki.mockablePublication.nowarn=true.",
        )

        val quiet = project.build("compile", "-Dlokki.mockablePublication.nowarn=true")
        assertFalse("lokki:interceptable" in quiet.output, quiet.output)
    }

    @Test
    fun testRebuildOfInterceptableMainWithoutClean() {
        assumeInterceptionPoints()
        val project = TestProject(projectDir)
        project.pom(compileConfiguration = INTERCEPTABLE_OPTIONS)
        project.write("src/main/kotlin/sample/Clock.kt", CLOCK)
        project.write("src/test/kotlin/sample/ClockTest.kt", CLOCK_TEST)

        project.build("test", "-Dlokki.mockablePublication.nowarn=true")
        project.build("test", "-Dlokki.mockablePublication.nowarn=true")

        assertEquals(3, project.testCount("sample.ClockTest"))
    }

    @Test
    fun testInterceptableInPluginConfiguration() {
        val project = TestProject(projectDir)
        project.pom(configuration = INTERCEPTABLE_OPTIONS)
        project.write("src/main/kotlin/sample/Clock.kt", CLOCK)
        project.write("src/test/kotlin/sample/ClockTest.kt", CLOCK_TEST)

        val failure = project.buildAndFail("test")

        assertContains(
            failure.output,
            "lokki:interceptable changes the main code, but the execution 'test-compile' of " +
                "kotlin-maven-plugin passes it to the test compilation too. Set <pluginOptions> with " +
                "lokki:interceptable in the configuration of the execution of the compile goal instead of the " +
                "configuration of the plugin.",
        )
    }

    @Test
    fun testDiagnostics() {
        val project = TestProject(projectDir)
        project.pom()
        project.write("src/main/kotlin/sample/Bolt.kt", "package sample\n\nclass Bolt\n")
        project.write(
            "src/test/kotlin/sample/BoltTest.kt",
            """
            package sample

            import io.lokki.mock

            fun bolt(): Bolt = mock<Bolt>()
            """,
        )

        val failure = project.buildAndFail("test-compile")

        val errors = failure.output.lines().filter { it.startsWith("[ERROR]") && "BoltTest.kt" in it }
        assertTrue(errors.any { "'sample.Bolt' cannot be mocked: it is a final class." in it }, failure.output)
    }

    @Test
    fun testIncrementalCompilationOfNewMemberInTestSources() {
        val project = TestProject(projectDir)
        project.pom(properties = "<kotlin.compiler.incremental>true</kotlin.compiler.incremental>")
        project.write("src/test/kotlin/sample/Car.kt", "package sample\n\ninterface Car {\n    fun drive(): String\n}\n")
        project.write("src/test/kotlin/sample/Driver.kt", exercise("drive()"))
        project.write("src/test/kotlin/sample/CarTest.kt", CAR_TEST)
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/test/kotlin/sample/Car.kt",
            "package sample\n\ninterface Car {\n    fun drive(): String\n    fun stop()\n}\n",
        )
        project.write("src/test/kotlin/sample/Driver.kt", exercise("stop()"))
        project.build("test")

        assertEquals(1, project.testCount("sample.CarTest"))
    }

    private fun assumeInterceptionPoints() {
        val (major, minor, patch) = KOTLIN_VERSION.substringBefore('-').split('.').map(String::toInt)
        assumeTrue(KotlinVersion(major, minor, patch) >= KotlinVersion(2, 4, 20)) {
            "Kotlin $KOTLIN_VERSION generates no interception points"
        }
    }

    private companion object {
        @JvmStatic
        fun testedKotlin(): List<String> = TESTED_KOTLIN
    }
}

private const val INTERCEPTABLE_OPTIONS =
    "<pluginOptions><option>lokki:interceptable=sample.Clock</option><option>lokki:interceptable=sample.now</option>" +
        "</pluginOptions>"

private const val GARAGE: String =
    """
    package sample

    interface Car {
        fun drive(speed: Int): String
    }

    class Garage(private val car: Car) {
        fun test(): String = car.drive(60)
    }
    """

private const val GARAGE_TEST_COUNT = 2
private const val GARAGE_TEST: String =
    """
    package sample

    import io.lokki.Mock
    import io.lokki.every
    import io.lokki.junit5.LokkiExtension
    import io.lokki.mock
    import io.lokki.verify
    import org.junit.jupiter.api.Assertions.assertEquals
    import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.extension.ExtendWith

    @ExtendWith(LokkiExtension::class)
    class GarageTest {
        @Mock
        lateinit var car: Car

        @Test
        fun stubsMockField() {
            every { car.drive(any()) } returns "vroom"

            assertEquals("vroom", Garage(car).test())
            verify { car.drive(60) }
        }

        @Test
        fun stubsWithMatchers() {
            val car = mock<Car>()
            every { car.drive(more(50)) } returns "fast"
            every { car.drive(less(50)) } answers { "slow at ${'$'}{firstArg<Int>()}" }

            assertEquals("fast", Garage(car).test())
            assertEquals("slow at 5", car.drive(5))
            verify(exactly = 2) { car.drive(any()) }
        }
    }
    """

private const val CLOCK: String =
    """
    package sample

    object Clock {
        fun zone(): String = "UTC"
    }

    fun now(): Long = 1
    """

private const val CLOCK_TEST: String =
    """
    package sample

    import io.lokki.every
    import io.lokki.junit5.LokkiExtension
    import io.lokki.mockObject
    import io.lokki.mockStatic
    import org.junit.jupiter.api.Assertions.assertEquals
    import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.extension.ExtendWith

    @ExtendWith(LokkiExtension::class)
    class ClockTest {
        @Test
        fun mocksObject() {
            mockObject(Clock)
            every { Clock.zone() } returns "CET"

            assertEquals("CET", Clock.zone())
        }

        @Test
        fun mocksTopLevelFunction() {
            mockStatic(::now)
            every { now() } returns 42L

            assertEquals(42L, now())
        }

        @Test
        fun objectIsRestoredAfterTest() {
            assertEquals("UTC", Clock.zone())
            assertEquals(1L, now())
        }
    }
    """

private fun exercise(call: String): String =
    """
    package sample

    fun exercise(car: Car): Any? = car.$call
    """

private const val CAR_TEST: String =
    """
    package sample

    import io.lokki.LokkiException
    import io.lokki.mock
    import org.junit.jupiter.api.Assertions.assertThrows
    import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

    class CarTest {
        @Test
        fun strictMockHasNoAnswer() {
            assertThrows(LokkiException::class.java) { exercise(mock<Car>()) }
        }
    }
    """

private const val UNRELATED_TEST: String =
    """
    package sample

    import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

    class UnrelatedTest {
        @Test
        fun passes() {
        }
    }
    """
