package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ParcelizeTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testParcelableDeclarationsOfMockablePackage(gradle: GradleUnderTest) {
        build(gradle, listOf(LOKKI_PLUGIN, PARCELIZE_PLUGIN))
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testParcelizePluginAppliedFirst(gradle: GradleUnderTest) {
        build(gradle, listOf(PARCELIZE_PLUGIN, LOKKI_PLUGIN))
    }

    private fun build(gradle: GradleUnderTest, plugins: List<String>) {
        assumeInterceptionPoints()
        val project = TestProject(projectDir, gradle, android = true)
        val kotlinAndroid = if (gradle == GradleUnderTest.MINIMAL) "" else " apply false"
        project.write(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.library") version "${gradle.agpVersion}"
                kotlin("android") version "$KOTLIN_VERSION"$kotlinAndroid
                ${plugins.joinToString("\n                ")}
            }

            android {
                namespace = "sample"
                compileSdk = 35
                defaultConfig {
                    minSdk = 24
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            lokki {
                interceptable("sample.**")
            }

            dependencies {
                testImplementation("junit:junit:4.13.2")
            }
            """,
        )
        project.write(
            "src/main/kotlin/sample/Cars.kt",
            """
            package sample

            import android.os.Parcelable
            import kotlinx.parcelize.Parcelize

            @Parcelize
            class Car(val seats: Int) : Parcelable {
                fun describe(): String = "car/${'$'}seats"
            }

            @Parcelize
            object Garage : Parcelable {
                fun park(): String = "parked"
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/CarTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.lokkiScope
            import io.lokki.mock
            import io.lokki.mockConstructor
            import io.lokki.mockObject
            import org.junit.Assert.assertEquals
            import org.junit.Test

            class CarTest {
                @Test
                fun mocksParcelableDeclarations() {
                    lokkiScope {
                        val car = mock<Car>()
                        every { car.describe() } returns "mocked"
                        assertEquals("mocked", car.describe())
                        mockObject(Garage)
                        every { Garage.park() } returns "stub"
                        assertEquals("stub", Garage.park())
                        mockConstructor(Car::class)
                        every { anyConstructed<Car>().describe() } returns "constructed"
                        assertEquals("constructed", Car(2).describe())
                    }
                    assertEquals("car/2", Car(2).describe())
                    assertEquals("parked", Garage.park())
                }
            }
            """,
        )

        val result = project.build("testDebugUnitTest")

        assertEquals(TaskOutcome.SUCCESS, result.task(":testDebugUnitTest")?.outcome)
        val creator = projectDir.resolve("build").walk().first { it.name == "Car\$Creator.class" }
        assertContains(creator.readText(Charsets.ISO_8859_1), "Car\$LokkiConstructorsHolder")
    }
}

private val PARCELIZE_PLUGIN = "kotlin(\"plugin.parcelize\") version \"$KOTLIN_VERSION\""
