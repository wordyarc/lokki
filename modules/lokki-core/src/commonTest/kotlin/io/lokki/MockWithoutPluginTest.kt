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

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }

    @Test
    fun testRegularObjectIsNotMock() {
        assertFalse(isMock(object : Car {}))
    }
}
