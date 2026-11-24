package io.lokki.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MissingClassesTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testMockOfTypeWithClassesMissingFromTestClasspath(gradle: GradleUnderTest) {
        val project = projectWithOptionalDependency(gradle)
        project.write(
            "library/src/main/kotlin/library/Store.kt",
            """
            package library

            import optional.Codec

            abstract class Store {
                abstract fun load(id: Int): String

                abstract fun codecs(): List<Codec>
            }

            abstract class Plugin {
                open fun codec(): Codec? = null

                abstract fun id(): Int
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/StoreTest.kt",
            """
            package sample

            import io.lokki.every
            import io.lokki.mock
            import library.Store
            import kotlin.test.Test
            import kotlin.test.assertEquals

            class StoreTest {
                @Test
                fun mockOfTypeWithMissingClassInTypeArgument() {
                    val store = mock<Store>()
                    every { store.load(1) } returns "one"
                    assertEquals("one", store.load(1))
                }
            }
            """,
        )

        assertEquals(TaskOutcome.SUCCESS, project.build("test").task(":test")?.outcome)

        project.write(
            "src/test/kotlin/sample/PluginTest.kt",
            """
            package sample

            import io.lokki.mock
            import library.Plugin

            fun plugin(): Plugin = mock<Plugin>()
            """,
        )
        val result = project.buildAndFail("test")

        assertEquals(TaskOutcome.FAILED, result.task(":compileTestKotlin")?.outcome)
        assertContains(
            result.output,
            "'library.Plugin' cannot be mocked: it or its supertypes declare members that refer to classes " +
                "missing from the classpath: 'codec' (optional.Codec).",
        )
    }

    @ParameterizedTest
    @EnumSource(GradleUnderTest::class)
    fun testChildMockOfTypeWithClassesMissingFromTestClasspath(gradle: GradleUnderTest) {
        val project = projectWithOptionalDependency(gradle)
        project.write(
            "library/src/main/kotlin/library/Sessions.kt",
            """
            package library

            import optional.Codec

            abstract class Connection {
                private fun codec(): Codec? = null

                fun isPlain(): Boolean = codec() == null
            }

            abstract class Session : Connection() {
                abstract fun id(): Int
            }

            interface Sessions {
                fun open(): Session
            }
            """,
        )
        project.write(
            "src/test/kotlin/sample/SessionsTest.kt",
            """
            @file:RelaxedChildMocks

            package sample

            import io.lokki.LokkiException
            import io.lokki.RelaxedChildMocks
            import io.lokki.every
            import io.lokki.mock
            import library.Session
            import library.Sessions
            import kotlin.test.Test
            import kotlin.test.assertContains
            import kotlin.test.assertFailsWith

            abstract class LocalSession : Session()

            interface LocalSessions {
                fun open(): LocalSession
            }

            class SessionsTest {
                @Test
                fun relaxedMockHasNoChildOfTypeWithMissingClass() {
                    val sessions = mock<Sessions>(relaxed = true)
                    val failure = assertFailsWith<LokkiException> { sessions.open() }
                    assertContains(failure.message.orEmpty(), "relaxed mock has no default for Session")
                }

                @Test
                fun relaxedMockHasNoChildOfSubclassOfTypeWithMissingClass() {
                    val sessions = mock<LocalSessions>(relaxed = true)
                    val failure = assertFailsWith<LokkiException> { sessions.open() }
                    assertContains(failure.message.orEmpty(), "relaxed mock has no default for LocalSession")
                }

                @Test
                fun linkHasNoChildOfTypeWithMissingClass() {
                    val sessions = mock<Sessions>()
                    val failure = assertFailsWith<LokkiException> { every { sessions.open().id() } returns 1 }
                    assertContains(
                        failure.message.orEmpty(),
                        "'library.Session' cannot be mocked: it or its supertypes declare members that refer to " +
                            "classes missing from the classpath: 'codec' (optional.Codec).",
                    )
                }
            }
            """,
        )

        assertEquals(TaskOutcome.SUCCESS, project.build("test").task(":test")?.outcome)
        assertEquals(3, project.testCount("sample.SessionsTest"))
    }

    private fun projectWithOptionalDependency(gradle: GradleUnderTest): TestProject {
        val project = TestProject(projectDir, gradle, subprojects = listOf("optional", "library"))
        project.write(
            "build.gradle.kts",
            """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("$LOKKI_PLUGIN_ID") version "$LOKKI_VERSION"
            }

            dependencies {
                testImplementation(project(":library"))
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
        project.write("optional/build.gradle.kts", "plugins { kotlin(\"jvm\") }")
        project.write("optional/src/main/kotlin/optional/Codec.kt", "package optional\n\nclass Codec")
        project.write(
            "library/build.gradle.kts",
            """
            plugins { kotlin("jvm") }

            dependencies {
                compileOnly(project(":optional"))
            }
            """,
        )
        return project
    }
}
