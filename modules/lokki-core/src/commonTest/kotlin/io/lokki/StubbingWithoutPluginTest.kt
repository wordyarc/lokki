package io.lokki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StubbingWithoutPluginTest {
    @Test
    fun testEveryThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { every { 1 } returns 2 }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }

    @Test
    fun testVerifyThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { verify { 1 } }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }
}
