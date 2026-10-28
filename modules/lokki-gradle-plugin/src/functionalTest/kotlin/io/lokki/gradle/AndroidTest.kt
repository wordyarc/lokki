package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testAndroidLibrary(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle, android = true)
        val agp8 = gradle == GradleUnderTest.MINIMAL
        val kotlinAndroid = if (agp8) "" else " apply false"
        project.write(
            "build.gradle.kts",
            """
            plugins {
                id("com.android.library") version "${gradle.agpVersion}"
                kotlin("android") version "$KOTLIN_VERSION"$kotlinAndroid
                id("io.lokki") version "$LOKKI_VERSION"
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
                testFixtures {
                    enable = ${!agp8}
                }
            }

            lokki {
                mockable("sample.Engine")
            }

            dependencies {
                testImplementation("junit:junit:4.13.2")
            }
            """,
        )
        project.write("src/main/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/main/kotlin/sample/Engine.kt", declaration("class Engine", "fun start(): String = \"started\""))
        project.write("src/test/kotlin/sample/CarTest.kt", ANDROID_UNIT_TEST)
        project.write("src/androidTest/kotlin/sample/DeviceCars.kt", "package sample\n\nfun deviceCar(): Car = io.lokki.mock<Car>()\n")
        project.write("src/testFixtures/kotlin/sample/Fixtures.kt", "package sample\n\nfun fixtureCar(): Car = io.lokki.mock<Car>()\n")

        val tasks = listOf("testDebugUnitTest", "compileDebugAndroidTestKotlin", "compileReleaseKotlin")
        val result = project.build(*(tasks + listOfNotNull("compileDebugTestFixturesKotlin".takeUnless { agp8 })).toTypedArray())

        assertEquals(TaskOutcome.SUCCESS, result.task(":testDebugUnitTest")?.outcome)
        assertTrue(project.hasClass("Car\$LokkiMock\$08768170\$DeviceCarsKt.class"))
        assertEquals(!agp8, project.hasClass("Car\$LokkiMock\$08768170\$FixturesKt.class"))
        assertContains(result.output, "compiled by :compileReleaseKotlin, and $RELEASE_WARNING")
        assertFalse("compiled by :compileDebugKotlin" in result.output, result.output)
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testAndroidKotlinMultiplatformLibrary(gradle: GradleUnderTest) {
        val project = TestProject(projectDir, gradle, android = true)
        val android = if (gradle == GradleUnderTest.MINIMAL) {
            "androidLibrary" to "withHostTestBuilder {}\n        withDeviceTestBuilder {}"
        } else {
            "android" to "withHostTest {}\n        withDeviceTest {}"
        }
        project.write(
            "build.gradle.kts",
            """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget
            import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

            plugins {
                kotlin("multiplatform") version "$KOTLIN_VERSION"
                id("com.android.kotlin.multiplatform.library") version "${gradle.agpVersion}"
                id("io.lokki") version "$LOKKI_VERSION"
            }

            kotlin {
                ${android.first} {
                    namespace = "sample"
                    compileSdk = 35
                    minSdk = 24
                    ${android.second}
                }

                sourceSets {
                    getByName("androidHostTest").dependencies {
                        implementation("junit:junit:4.13.2")
                    }
                }
            }

            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions.jvmTarget = JvmTarget.JVM_17
            }
            """,
        )
        project.write("src/commonMain/kotlin/sample/Car.kt", declaration("interface Car", "fun drive(): String"))
        project.write("src/androidHostTest/kotlin/sample/CarTest.kt", ANDROID_KMP_UNIT_TEST)
        project.write(
            "src/androidDeviceTest/kotlin/sample/DeviceCars.kt",
            "package sample\n\nfun deviceCar(): Car = io.lokki.mock<Car>()\n",
        )

        val result = project.build("testAndroidHostTest", "compileAndroidDeviceTest")

        assertEquals(TaskOutcome.SUCCESS, result.task(":testAndroidHostTest")?.outcome)
        assertTrue(project.hasClass("Car\$LokkiMock\$08768170\$DeviceCarsKt.class"))
    }

    private fun TestProject.hasClass(name: String): Boolean = projectDir.resolve("build").walk().any { it.name == name }
}

private const val RELEASE_WARNING = "the APK or AAR of this Android variant would contain this bytecode."

private const val ANDROID_UNIT_TEST = """
package sample

import io.lokki.every
import io.lokki.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class CarTest {
    @Test
    fun mocksInterfaceAndMockableClass() {
        val car = mock<Car>()
        every { car.drive() } returns "driven"
        val engine = mock<Engine>()
        every { engine.start() } returns "mocked"

        assertEquals("driven", car.drive())
        assertEquals("mocked", engine.start())
    }
}
"""

private const val ANDROID_KMP_UNIT_TEST = """
package sample

import io.lokki.every
import io.lokki.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class CarTest {
    @Test
    fun mocksInterface() {
        val car = mock<Car>()
        every { car.drive() } returns "driven"

        assertEquals("driven", car.drive())
    }
}
"""
