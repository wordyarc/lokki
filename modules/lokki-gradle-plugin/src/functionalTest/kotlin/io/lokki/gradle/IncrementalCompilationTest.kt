package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalCompilationTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfInterfaceInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write("src/test/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))
        project.write("src/test/kotlin/sample/GarageTest.kt", mockTest("GarageTest", "Car"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")
        assertEquals(setOf("drive()"), project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$CarTestKt"))

        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("interface Car", "fun drive(): String", "fun stop()"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "stop()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt", "GarageTest.kt"), project.compiledSources(":compileTestKotlin"))
        for (filePart in listOf("CarTestKt", "GarageTestKt")) {
            assertEquals(
                setOf("drive()", "stop()"),
                project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$$filePart"),
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testChangedMemberSignatureInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write("src/test/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(distance: Int): String"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive(1)"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf("drive(int)"), project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$CarTestKt"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testRemovedSuspendMemberWithContinuationInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("interface Car", "fun drive(): String", "suspend fun plate(): Result<String>"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write("src/test/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        val continuation = projectDir.resolve(
            "build/classes/kotlin/test/sample/Car\$LokkiMock\$08768170\$CarTestKt\$plate\$1.class",
        )

        project.build("test")
        assertTrue(continuation.exists())

        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf("drive()"), project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$CarTestKt"))
        assertFalse(continuation.exists())
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testChangedReturnTypeOfRelaxedMemberInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun plate(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", relaxedPlate("\"\""))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarTest {
                @Test
                fun relaxedMockAnswersWithTheDefaultOfTheReturnType() {
                    assertEquals(relaxedPlate(), exercise(mock<Car>(relaxed = true)))
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun plate(): String?"))
        project.write("src/main/kotlin/sample/Driver.kt", relaxedPlate("null"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfSupertypeInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Vehicle.kt", declaration("interface Vehicle", "fun park()"))
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car : Vehicle", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "park()"))
        project.write("src/test/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write("src/main/kotlin/sample/Vehicle.kt", declaration("interface Vehicle", "fun park()", "fun honk()"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "honk()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("drive()", "park()", "honk()"),
            project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$CarTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfGenericInterfaceInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Repo.kt", declaration("interface Repo<T>", "fun find(): T"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Repo<String>", "find()"))
        project.write("src/test/kotlin/sample/RepoTest.kt", mockTest("RepoTest", "Repo<String>"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Repo.kt",
            declaration("interface Repo<T>", "fun find(): T", "fun save(value: T)"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Repo<String>", "save(\"x\")"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("RepoTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("find()", "save(java.lang.String)"),
            project.mockedMethods("test", "sample.Repo\$LokkiMock\$3fcb1566\$RepoTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfAdditionalInterfaceInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Named.kt", declaration("interface Named", "fun name(): String"))
        project.write("src/main/kotlin/sample/Driver.kt", additionalInterfaceExercise("name()"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            mockTest("CarTest", "Named", mock = "mock<Car>(moreInterfaces = arrayOf(Named::class)) as Named"),
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Named.kt",
            declaration("interface Named", "fun name(): String", "fun rename(name: String)"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", additionalInterfaceExercise("rename(\"x\")"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("drive()", "name()", "rename(java.lang.String)"),
            project.mockedMethods("test", "sample.Car\$LokkiMock\$a7d52d8b\$CarTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewAbstractMemberOfClassInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write(
            "src/main/kotlin/sample/Engine.kt",
            declaration("abstract class Engine", "abstract fun start(): String"),
        )
        project.write("src/main/kotlin/sample/Mechanic.kt", exercise("Engine", "start()"))
        project.write("src/test/kotlin/sample/EngineTest.kt", mockTest("EngineTest", "Engine"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")
        assertEquals(
            setOf("start()"),
            project.mockedMethods("test", "sample.Engine\$LokkiMock\$dabe4326\$EngineTestKt"),
        )

        project.write(
            "src/main/kotlin/sample/Engine.kt",
            declaration("abstract class Engine", "abstract fun start(): String", "abstract fun stop()"),
        )
        project.write("src/main/kotlin/sample/Mechanic.kt", exercise("Engine", "stop()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("EngineTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("start()", "stop()"),
            project.mockedMethods("test", "sample.Engine\$LokkiMock\$dabe4326\$EngineTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewOpenMemberOfSuperclassInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Vehicle.kt", declaration("open class Vehicle", "open fun park() {}"))
        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("open class Car : Vehicle()", "open fun drive(): String = \"driven\""),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "park()"))
        project.write("src/test/kotlin/sample/CarTest.kt", mockTest("CarTest", "Car"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Vehicle.kt",
            declaration("open class Vehicle", "open fun park() {}", "open fun honk() {}"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "honk()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("drive()", "park()", "honk()"),
            project.mockedMethods("test", "sample.Car\$LokkiMock\$08768170\$CarTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testImplementedSuspendMemberOfSuperclassInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write(
            "src/main/kotlin/sample/Vehicle.kt",
            declaration("abstract class Vehicle", "abstract suspend fun park(): String"),
        )
        project.write("src/main/kotlin/sample/Car.kt", declaration("abstract class Car : Vehicle()"))
        project.write("src/main/kotlin/sample/Driver.kt", parking("no original implementation"))
        project.write(
            "src/test/kotlin/sample/Stubs.kt",
            """
            package sample

            import io.lokki.AnswerScope
            import io.lokki.coEvery

            fun stubOriginalParking(car: Car) {
                coEvery { car.park() } coAnswers { original() }
            }

            private suspend fun AnswerScope<String>.original(): String = coCallOriginal()
            """,
        )
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarTest {
                @Test
                fun answerCallsTheOriginal() {
                    val car = mock<Car>()
                    stubOriginalParking(car)

                    assertEquals(expectedParking(), parkingOf(car))
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Vehicle.kt",
            declaration("abstract class Vehicle", "open suspend fun park(): String = \"parked\""),
        )
        project.write("src/main/kotlin/sample/Driver.kt", parking("parked"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("Stubs.kt", "CarTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfInterfaceInSameCompilation(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/test/kotlin/sample/Boat.kt", declaration("interface Boat", "fun sail(): String"))
        project.write("src/test/kotlin/sample/Sailor.kt", exercise("Boat", "sail()"))
        project.write("src/test/kotlin/sample/BoatTest.kt", mockTest("BoatTest", "Boat"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/test/kotlin/sample/Boat.kt",
            declaration("interface Boat", "fun sail(): String", "fun moor()"),
        )
        project.write("src/test/kotlin/sample/Sailor.kt", exercise("Boat", "moor()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("Boat.kt", "Sailor.kt", "BoatTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(
            setOf("sail()", "moor()"),
            project.mockedMethods("test", "sample.Boat\$LokkiMock\$06596b24\$BoatTestKt"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfInterfaceMockedInInlineFixtureOfAnotherProject(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle, subprojects = listOf("core", "app"))
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION" apply false
                id("io.lokki") version "$LOKKI_VERSION" apply false
            }
            """,
        )
        project.write(
            "core/build.gradle.kts",
            """
            plugins {
                kotlin("jvm")
                `java-test-fixtures`
                id("io.lokki")
            }
            """,
        )
        project.write(
            "app/build.gradle.kts",
            """
            plugins {
                kotlin("jvm")
                id("io.lokki")
            }

            dependencies {
                testImplementation(project(":core"))
                testImplementation(testFixtures(project(":core")))
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
        project.write("core/src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("core/src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write(
            "core/src/testFixtures/kotlin/sample/Fixtures.kt",
            """
            package sample

            import io.lokki.mock

            inline fun carFixture(): Car = mock<Car>()
            """,
        )
        project.write(
            "app/src/test/kotlin/app/AppTest.kt",
            """
            package app

            import io.lokki.LokkiException
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertFailsWith
            import sample.carFixture
            import sample.exercise
            import sample.exercisedMember

            class AppTest {
                @Test
                fun strictMockHasNoAnswer() {
                    val exception = assertFailsWith<LokkiException> { exercise(carFixture()) }
                    val member = exception.message?.replace(Regex("(?s)\\(#\\d+\\)\\.(\\w+)\\(.*"), ".${'$'}1")
                    assertEquals("no answer found for ${'$'}{exercisedMember()}", member)
                }
            }
            """,
        )

        project.build(":app:test")

        project.write(
            "core/src/main/kotlin/sample/Car.kt",
            declaration("interface Car", "fun drive(): String", "fun stop()"),
        )
        project.write("core/src/main/kotlin/sample/Driver.kt", exercise("Car", "stop()"))
        val result = project.build(":app:test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome)
        assertEquals(setOf("Fixtures.kt"), project.compiledSources(":core:compileTestFixturesKotlin"))
        assertEquals(
            setOf("drive()", "stop()"),
            project.mockedMethods("testFixtures", "sample.Car\$LokkiMock\$08768170\$FixturesKt", project = "core"),
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testChangedDefaultValueOfStubbedMemberInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Radio.kt", radio(defaultVolume = 5))
        project.write(
            "src/test/kotlin/sample/RadioTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class RadioTest {
                @Test
                fun omittedDefaultIsMatchedByItsValue() {
                    val radio = mock<Radio>()
                    every { radio.tune(any()) } returns "news"

                    assertEquals("news", news(radio))
                }
            }
            """,
        )

        project.build("test")

        project.write("src/main/kotlin/sample/Radio.kt", radio(defaultVolume = 7))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(emptySet(), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewConstructorParameterOfSpiedClassInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Engine.kt", engine("private val power: Int = 2", "\"${'$'}power\""))
        project.write(
            "src/test/kotlin/sample/EngineTest.kt",
            """
            package sample

            import io.lokki.spy
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class EngineTest {
                @Test
                fun spyCallsTheConstructor() {
                    assertEquals(expectedDescription(), spy<Engine>().describe())
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Engine.kt",
            engine("private val power: Int = 2, private val unit: String = \"kW\"", "\"${'$'}power ${'$'}unit\""),
        )
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("EngineTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewConstructorParameterOfInjectedClassInMain(gradle: GradleUnderTest) {
        val project = injectionProject(gradle)

        project.write(
            "src/main/kotlin/sample/Garage.kt",
            garage("private val car: Car, private val door: Door", "car.drive() + door.state()"),
        )
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("GarageTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewPropertyOfInjectedClassInMain(gradle: GradleUnderTest) {
        val project = injectionProject(gradle)

        project.write(
            "src/main/kotlin/sample/Garage.kt",
            garage("private val car: Car", "car.drive() + door.state()", "lateinit var door: Door"),
        )
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("GarageTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testFinalClassRemovedFromMockable(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write("build.gradle.kts", mockableBuildScript("sample.Engine"))
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))
        project.write("src/main/kotlin/sample/Mechanic.kt", exercise("Engine", "start()"))
        project.write("src/test/kotlin/sample/EngineTest.kt", mockTest("EngineTest", "Engine"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        project.build("test")

        project.write("build.gradle.kts", mockableBuildScript("sample.Gear"))
        val failure = project.buildAndFail("test")

        assertTrue("'sample.Engine' cannot be mocked: it is a final class" in failure.output, failure.output)

        project.write("build.gradle.kts", mockableBuildScript("sample.Engine"))
        project.build("test")

        assertEquals(setOf("start()"), project.mockedMethods("test", ENGINE_MOCK))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfMockableObjectInMain(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write("build.gradle.kts", mockableBuildScript("sample.Clock"))
        project.write("src/main/kotlin/sample/Clock.kt", declaration("object Clock", "fun now(): Long = 1"))
        project.write("src/test/kotlin/sample/ClockTest.kt", OBJECT_TEST)
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        project.build("test")
        assertEquals(setOf("now()"), project.mockedMethods("test", CLOCK_REDIRECT_MOCK))

        project.write(
            "src/main/kotlin/sample/Clock.kt",
            declaration("object Clock", "fun now(): Long = 1", "fun zone(): String = \"UTC\""),
        )
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("ClockTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf("now()", "zone()"), project.mockedMethods("test", CLOCK_REDIRECT_MOCK))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testObjectRemovedFromMockable(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write("build.gradle.kts", mockableBuildScript("sample.Clock"))
        project.write("src/main/kotlin/sample/Clock.kt", declaration("object Clock", "fun now(): Long = 1"))
        project.write("src/test/kotlin/sample/ClockTest.kt", OBJECT_TEST)
        project.write(
            "src/test/kotlin/sample/MockObjectTest.kt",
            """
            package sample

            import io.lokki.lokkiScope
            import io.lokki.mockObject
            import kotlin.test.Test

            class MockObjectTest {
                @Test
                fun mocksObject() {
                    lokkiScope { mockObject(Clock) }
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        project.build("test")

        project.write("build.gradle.kts", mockableBuildScript("sample.Gear"))
        val failure = project.buildAndFail("test")

        val errors = failure.output.lines().filter { it.startsWith("e: ") }
        val noPoints = "'sample.Clock' cannot be intercepted: it has no interception points"
        assertTrue(errors.any { "MockObjectTest.kt" in it && noPoints in it }, failure.output)
        assertTrue(errors.any { "ClockTest.kt" in it && "A call of 'now' cannot be intercepted" in it }, failure.output)

        project.write("build.gradle.kts", mockableBuildScript("sample.Clock"))
        project.build("test")

        assertEquals(setOf("now()"), project.mockedMethods("test", CLOCK_REDIRECT_MOCK))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testOpenClassRemovedFromMockable(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle)
        project.write("build.gradle.kts", mockableBuildScript("sample.Engine"))
        project.write(
            "src/main/kotlin/sample/Engine.kt",
            declaration("open class Engine", "fun start(): String = \"started\""),
        )
        project.write(
            "src/test/kotlin/sample/EngineTest.kt",
            """
            package sample

            import io.lokki.isMock
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertTrue

            class EngineTest {
                @Test
                fun createsMock() {
                    assertTrue(isMock(mock<Engine>()))
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        project.build("test")
        assertEquals(setOf("start()"), project.mockedMethods("test", ENGINE_MOCK))

        project.write("build.gradle.kts", mockableBuildScript("sample.Gear"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("EngineTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(emptySet(), project.mockedMethods("test", ENGINE_MOCK))
    }

    private fun mockableBuildScript(pattern: String): String =
        """
        plugins {
            kotlin("jvm") version "$KOTLIN_VERSION"
            id("io.lokki") version "$LOKKI_VERSION"
        }

        lokki {
            mockable("$pattern")
        }

        testing {
            suites {
                named<JvmTestSuite>("test") {
                    useKotlinTest("$KOTLIN_VERSION")
                }
            }
        }
        """

    private fun injectionProject(gradle: GradleUnderTest): TestProject {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Door.kt", declaration("interface Door", "fun state(): String"))
        project.write("src/main/kotlin/sample/Garage.kt", garage("private val car: Car", "car.drive()"))
        project.write(
            "src/test/kotlin/sample/GarageTest.kt",
            """
            package sample

            import io.lokki.InjectMocks
            import io.lokki.Mock
            import io.lokki.MockAnnotations
            import io.lokki.RelaxedMock
            import io.lokki.every
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class GarageTest {
                @Mock
                lateinit var car: Car

                @RelaxedMock
                lateinit var door: Door

                @InjectMocks
                lateinit var garage: Garage

                @Test
                fun injectsTheMocks() {
                    MockAnnotations.init(this)
                    every { car.drive() } returns "driven"
                    assertEquals("driven", garage.open())
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        project.build("test")
        return project
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewOpenMemberOfSpiedClassInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("open class Car", "open fun drive(): String = \"driven\""),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "drive()"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.confirmVerified
            import io.lokki.spy
            import kotlin.test.Test
            import kotlin.test.assertContains
            import kotlin.test.assertFailsWith

            class CarTest {
                @Test
                fun spyRecordsTheCall() {
                    val car = spy(Car())
                    exercise(car)

                    val error = assertFailsWith<AssertionError> { confirmVerified(car) }
                    assertContains(error.message.orEmpty(), exercisedMember().substringAfter('.') + "()")
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")

        project.write(
            "src/main/kotlin/sample/Car.kt",
            declaration("open class Car", "open fun drive(): String = \"driven\"", "open fun park() {}"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Car", "park()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfChildMockTypeInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write(
            "src/main/kotlin/sample/Door.kt",
            declaration("interface Door", "fun open(): Boolean", "fun knock()"),
        )
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun door(index: Int): Door"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Door", "knock()"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            @file:ChainedMocks

            package sample

            import io.lokki.ChainedMocks
            import io.lokki.LokkiException
            import io.lokki.every
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertFailsWith

            class CarTest {
                @Test
                fun childMockHasNoAnswer() {
                    val car = mock<Car>()
                    every { car.door(1).open() } returns true

                    val exception = assertFailsWith<LokkiException> { exercise(car.door(1)) }
                    val child = Regex("(?s)\\(child of #\\d+#\\d+\\)\\.(\\w+)\\(.*")
                    val member = exception.message?.replace(child, ".${'$'}1")
                    assertEquals("no answer found for ${'$'}{exercisedMember()}", member)
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        val child = "sample.Door\$LokkiMock\$065a5592\$CarTestKt"

        project.build("test")
        assertEquals(setOf("open()", "knock()"), project.mockedMethods("test", child))

        project.write(
            "src/main/kotlin/sample/Door.kt",
            declaration("interface Door", "fun open(): Boolean", "fun knock()", "fun close()"),
        )
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Door", "close()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf("open()", "knock()", "close()"), project.mockedMethods("test", child))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testNewMemberOfRelaxedChildTypeInMain(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/main/kotlin/sample/Door.kt", declaration("interface Door", "fun knock()"))
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun door(index: Int): Door"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Door", "knock()"))
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            @file:ChainedMocks

            package sample

            import io.lokki.ChainedMocks
            import io.lokki.mock
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarTest {
                @Test
                fun relaxedChildAnswersWithTheDefault() {
                    assertEquals(Unit, exercise(mock<Car>(relaxed = true).door(1)))
                }
            }
            """,
        )
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)
        val child = "sample.Door\$LokkiMock\$065a5592\$CarTestKt"

        project.build("test")
        assertEquals(setOf("knock()"), project.mockedMethods("test", child))

        project.write("src/main/kotlin/sample/Door.kt", declaration("interface Door", "fun knock()", "fun close()"))
        project.write("src/main/kotlin/sample/Driver.kt", exercise("Door", "close()"))
        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf("knock()", "close()"), project.mockedMethods("test", child))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testFirstMockFieldOfBaseClassInSameCompilation(gradle: GradleUnderTest) {
        val project = jvmProject(gradle)
        project.write("src/test/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/test/kotlin/sample/BaseTest.kt", baseTest(mockField = false))
        project.write("src/test/kotlin/sample/CarTest.kt", mockFieldsTest("sample"))
        project.write("src/test/kotlin/sample/UnrelatedTest.kt", UNRELATED_TEST)

        project.build("test")
        assertEquals(setOf(MOCK_FIELDS_OWNER), project.interfaces("test", "sample.CarTest"))

        project.write("src/test/kotlin/sample/BaseTest.kt", baseTest(mockField = true))
        val added = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, added.task(":test")?.outcome)
        assertEquals(setOf("BaseTest.kt", "CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(emptySet(), project.interfaces("test", "sample.CarTest"))

        project.write("src/test/kotlin/sample/CarTest.kt", mockFieldsTest("sample", "otherCar"))
        val subclass = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, subclass.task(":test")?.outcome)
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertTrue(INIT_MOCK_FIELDS in project.syntheticMethods("test", "sample.CarTest"))

        project.write("src/test/kotlin/sample/BaseTest.kt", baseTest(mockField = false))
        val removed = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, removed.task(":test")?.outcome)
        assertEquals(setOf("BaseTest.kt", "CarTest.kt"), project.compiledSources(":compileTestKotlin"))
        assertEquals(setOf(MOCK_FIELDS_OWNER), project.interfaces("test", "sample.CarTest"))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testFirstMockFieldOfBaseClassInTestFixturesOfAnotherProject(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle, subprojects = listOf("core", "app"))
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION" apply false
                id("io.lokki") version "$LOKKI_VERSION" apply false
            }
            """,
        )
        project.write(
            "core/build.gradle.kts",
            """
            plugins {
                kotlin("jvm")
                `java-test-fixtures`
                id("io.lokki")
            }
            """,
        )
        project.write(
            "app/build.gradle.kts",
            """
            plugins {
                kotlin("jvm")
                id("io.lokki")
            }

            dependencies {
                testImplementation(testFixtures(project(":core")))
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
        project.write("core/src/testFixtures/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("core/src/testFixtures/kotlin/sample/BaseTest.kt", baseTest(mockField = false))
        project.write("app/src/test/kotlin/app/CarTest.kt", mockFieldsTest("app"))
        project.write("app/src/test/kotlin/app/UnrelatedTest.kt", UNRELATED_TEST.replace("package sample", "package app"))

        project.build(":app:test")

        project.write("core/src/testFixtures/kotlin/sample/BaseTest.kt", baseTest(mockField = true))
        val result = project.build(":app:test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":app:test")?.outcome)
        assertEquals(setOf("BaseTest.kt"), project.compiledSources(":core:compileTestFixturesKotlin"))
        assertEquals(setOf("CarTest.kt"), project.compiledSources(":app:compileTestKotlin"))
    }

    private fun baseTest(mockField: Boolean): String =
        """
        package sample

        import io.lokki.Mock
        import io.lokki.isMock

        abstract class BaseTest {
            ${if (mockField) "@Mock lateinit var baseCar: Car" else ""}

            fun baseMocks(): Int = ${if (mockField) "if (isMock(baseCar)) 1 else 0" else "0"}

            fun baseFields(): Int = ${if (mockField) 1 else 0}
        }
        """

    private fun mockFieldsTest(packageName: String, vararg names: String): String {
        val fields = listOf("car") + names
        return """
            package $packageName

            import io.lokki.Mock
            import io.lokki.MockAnnotations
            import io.lokki.isMock
            import kotlin.test.Test
            import kotlin.test.assertEquals
            import kotlin.test.assertTrue
            import sample.BaseTest
            import sample.Car

            class CarTest : BaseTest() {
                ${fields.joinToString("\n                ") { "@Mock lateinit var $it: Car" }}

                @Test
                fun initializesTheMockFieldsOfTheHierarchy() {
                    MockAnnotations.init(this)
                    assertTrue(${fields.joinToString(" && ") { "isMock($it)" }})
                    assertEquals(baseFields(), baseMocks())
                }
            }
            """
    }

    private fun engine(parameters: String, description: String): String =
        """
        package sample

        open class Engine($parameters) {
            open fun describe(): String = $description
        }

        fun expectedDescription(): String = Engine().describe()
        """

    private fun garage(parameters: String, opening: String, property: String = ""): String =
        """
        package sample

        class Garage($parameters) {
            $property

            fun open(): String = $opening
        }
        """

    private fun relaxedPlate(value: String): String =
        """
        package sample

        fun exercise(mock: Car): Any? = mock.plate()

        fun relaxedPlate(): String? = $value
        """

    private fun parking(expected: String): String =
        """
        package sample

        import kotlin.coroutines.Continuation
        import kotlin.coroutines.EmptyCoroutineContext
        import kotlin.coroutines.startCoroutine

        fun parkingOf(car: Car): String? {
            var outcome: Result<String>? = null
            suspend { car.park() }.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
            return outcome!!.fold({ it }, { it.message?.substringBefore(" for") })
        }

        fun expectedParking(): String = "$expected"
        """

    private fun radio(defaultVolume: Int): String =
        """
        package sample

        interface Radio {
            fun tune(station: Int, volume: Int = $defaultVolume): String
        }

        fun news(radio: Radio): String = radio.tune(101)
        """

    private fun jvmProject(gradle: GradleUnderTest): TestProject =
        TestProject(projectDir, gradle).apply {
            write(
                "build.gradle.kts",
                """
                plugins {
                    kotlin("jvm") version "$KOTLIN_VERSION"
                    id("io.lokki") version "$LOKKI_VERSION"
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
}

private const val MOCK_FIELDS_OWNER = "io.lokki.internal.MockFieldsOwner"
private const val ENGINE_MOCK = "sample.Engine\$LokkiMock\$dabe4326\$EngineTestKt"
private const val CLOCK_REDIRECT_MOCK = "sample.LokkiRedirect\$LokkiMock\$40c3b43e\$ClockTestKt"
private const val OBJECT_TEST: String =
    """
    package sample

    import io.lokki.every
    import io.lokki.lokkiScope
    import io.lokki.mockObject
    import kotlin.test.Test
    import kotlin.test.assertEquals

    class ClockTest {
        @Test
        fun stubsObject() {
            lokkiScope {
                mockObject(Clock)
                every { Clock.now() } returns 2
                assertEquals(2, Clock.now())
            }
        }
    }
    """
private const val INIT_MOCK_FIELDS = "lokki\$initMockFields(int)"
