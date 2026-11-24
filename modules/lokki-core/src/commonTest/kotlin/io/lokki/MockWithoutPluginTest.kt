package io.lokki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MockWithoutPluginTest {
    interface Car

    @Test
    fun testMockThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { mock<Car>() }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }

    @Test
    fun testSpyThrowsWithoutCompilerPlugin() {
        assertFailsWith<LokkiException> { spy<Car>() }
        assertFailsWith<LokkiException> { spy<Car>(object : Car {}) }
    }

    @Test
    fun testRegularObjectIsNotMock() {
        assertFalse(isMock(object : Car {}))
    }
}

internal const val PLUGIN_NOT_APPLIED: String =
    "lokki compiler plugin is not applied to this compilation: with Gradle, name the compilation in " +
        "lokki { testCompilations(...) }, or set applyToMain = true for main code"
