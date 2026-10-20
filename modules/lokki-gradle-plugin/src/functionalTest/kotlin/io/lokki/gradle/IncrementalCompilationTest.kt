package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals

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

    private fun relaxedPlate(value: String): String =
        """
        package sample

        fun exercise(mock: Car): Any? = mock.plate()

        fun relaxedPlate(): String? = $value
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
