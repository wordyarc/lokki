package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals

class SerializationTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testSerializableDeclarationsOfMockablePackage(gradle: GradleUnderTest) {
        build(gradle, listOf(LOKKI_PLUGIN, SERIALIZATION_PLUGIN))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testSerializationPluginAppliedFirst(gradle: GradleUnderTest) {
        build(gradle, listOf(SERIALIZATION_PLUGIN, LOKKI_PLUGIN))
    }

    private fun build(gradle: GradleUnderTest, plugins: List<String>) {
        assumeInterceptionPoints()
        val project = TestProject(projectDir, gradle)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                ${plugins.joinToString("\n                ")}
            }

            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$SERIALIZATION_VERSION")
            }

            lokki {
                mockable("sample.**")
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
            "src/main/kotlin/sample/Cars.kt",
            """
            package sample

            import kotlinx.serialization.Serializable
            import kotlinx.serialization.json.Json

            @Serializable
            class Car(val name: String, val seats: Int = 4) {
                fun describe(): String = "${'$'}name/${'$'}seats"

                companion object {
                    fun default(): Car = Car("default")
                }
            }

            @Serializable
            object Config {
                fun label(): String = "config"
            }

            @Serializable
            sealed class Shape {
                @Serializable
                class Circle(val radius: Int) : Shape()
            }

            @Serializable
            enum class Color {
                RED;

                fun lower(): String = name.lowercase()
            }

            fun roundTrip(): String {
                val car = Json.decodeFromString<Car>(Json.encodeToString(Car("a", 2)))
                val config = Json.decodeFromString<Config>(Json.encodeToString(Config))
                val shape = Json.decodeFromString<Shape>(Json.encodeToString<Shape>(Shape.Circle(1)))
                val color = Json.decodeFromString<Color>(Json.encodeToString(Color.RED))
                return "${'$'}{car.describe()} ${'$'}{config.label()} ${'$'}{shape::class.simpleName} ${'$'}{color.lower()}"
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/CarsTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.lokkiScope
            import io.lokki.mock
            import io.lokki.mockConstructor
            import io.lokki.mockObject
            import kotlinx.serialization.json.Json
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class CarsTest {
                @Test
                fun serializesDeclarationsWithInterceptionPoints() {
                    assertEquals("a/2 config Circle red", roundTrip())
                }

                @Test
                fun mocksSerializableDeclarations() {
                    lokkiScope {
                        val car = mock<Car>()
                        every { car.describe() } returns "mocked"
                        mockObject(Config, Car, Color.RED)
                        every { Config.label() } returns "stub"
                        every { Car.default() } returns car
                        every { Color.RED.lower() } returns "stubbed"
                        assertEquals("stub", Config.label())
                        assertEquals("mocked", Car.default().describe())
                        assertEquals("stubbed", Color.RED.lower())
                        assertEquals("{}", Json.encodeToString(Config))
                    }
                    assertEquals("a/2 config Circle red", roundTrip())
                }

                @Test
                fun constructsDeserializedObjectsWithoutRedirect() {
                    lokkiScope {
                        mockConstructor(Car::class)
                        every { anyConstructed<Car>().describe() } returns "constructed"
                        assertEquals("constructed", Car("x").describe())
                        assertEquals("z/4", Json.decodeFromString<Car>("{\"name\":\"z\"}").describe())
                    }
                    assertEquals("x/4", Car("x").describe())
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(3, project.testCount("sample.CarsTest"))
    }
}

private val SERIALIZATION_PLUGIN = "kotlin(\"plugin.serialization\") version \"$KOTLIN_VERSION\""
