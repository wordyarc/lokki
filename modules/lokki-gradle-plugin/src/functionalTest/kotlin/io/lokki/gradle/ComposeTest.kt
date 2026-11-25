package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals

class ComposeTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testComposableDeclarationsOfMockablePackage(gradle: GradleUnderTest) {
        assumeInterceptionPoints()
        val project = TestProject(projectDir, gradle, google = true)
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                kotlin("plugin.compose") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            dependencies {
                implementation("androidx.compose.runtime:runtime:$COMPOSE_RUNTIME_VERSION")
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
            "src/main/kotlin/sample/Screens.kt",
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember

            object Screens {
                @Composable
                fun Title(text: String): String = remember(text) { "[${'$'}text]" }

                val subtitle: String
                    @Composable get() = remember { "subtitle" }

                fun plain(): String = "plain"
            }

            abstract class Base {
                @Composable
                fun Inherited(): String = remember { "inherited" }

                fun inheritedPlain(): String = "inherited plain"
            }

            object Derived : Base()

            enum class Tab {
                HOME {
                    @Composable
                    override fun Icon(): String = remember { "home" }
                };

                @Composable
                abstract fun Icon(): String

                @Composable
                fun Label(): String = remember { name }

                fun plain(): String = name
            }

            @Composable
            fun TopLevel(text: String): String = remember(text) { "<${'$'}text>" }

            val topLevelSubtitle: String
                @Composable get() = remember { "top-level subtitle" }

            fun topLevelPlain(): String = "top-level plain"

            @Composable
            fun Caller(): String =
                Screens.Title("a") + Screens.subtitle + Derived.Inherited() + Tab.HOME.Label() + Tab.HOME.Icon() +
                    TopLevel("b") +
                    topLevelSubtitle
            """,
        )
        project.write(
            "src/test/kotlin/sample/ScreensTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.lokkiScope
            import io.lokki.mockObject
            import io.lokki.mockStatic
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class ScreensTest {
                @Test
                fun interceptsMembersThatAreNotComposable() {
                    lokkiScope {
                        mockObject(Screens, Derived, Tab.HOME)
                        mockStatic(::topLevelPlain)
                        every { Screens.plain() } returns "mocked"
                        every { Derived.inheritedPlain() } returns "mocked inherited"
                        every { Tab.HOME.plain() } returns "mocked entry"
                        every { topLevelPlain() } returns "mocked top-level"
                        assertEquals("mocked", Screens.plain())
                        assertEquals("mocked inherited", Derived.inheritedPlain())
                        assertEquals("mocked entry", Tab.HOME.plain())
                        assertEquals("mocked top-level", topLevelPlain())
                    }
                    assertEquals("plain", Screens.plain())
                    assertEquals("top-level plain", topLevelPlain())
                }
            }
            """,
        )

        val result = project.build("test")

        assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome)
        assertEquals(1, project.testCount("sample.ScreensTest"))
    }
}
