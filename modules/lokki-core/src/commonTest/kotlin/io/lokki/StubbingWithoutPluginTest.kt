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
    fun testCoEveryThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { coEvery { 1 } returns 2 }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }

    @Test
    fun testJustRunThrowsWithoutCompilerPlugin() {
        val exceptions = listOf(
            assertFailsWith<LokkiException> { justRun { } },
            assertFailsWith<LokkiException> { coJustRun { } },
        )

        assertEquals(List(2) { "lokki compiler plugin is not applied to this compilation" }, exceptions.map { it.message })
    }

    @Test
    fun testVerifyThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { verify { 1 } }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }

    @Test
    fun testCoVerifyThrowsWithoutCompilerPlugin() {
        val exceptions = listOf(
            assertFailsWith<LokkiException> { coVerify { 1 } },
            assertFailsWith<LokkiException> { coVerifyCount { 1 * { 1 } } },
            assertFailsWith<LokkiException> { coExcludeRecords { 1 } },
        )

        assertEquals(List(3) { "lokki compiler plugin is not applied to this compilation" }, exceptions.map { it.message })
    }

    @Test
    fun testVerifyCountThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { verifyCount { 1 * { 1 } } }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }

    @Test
    fun testExcludeRecordsThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { excludeRecords { 1 } }

        assertEquals("lokki compiler plugin is not applied to this compilation", exception.message)
    }
}
