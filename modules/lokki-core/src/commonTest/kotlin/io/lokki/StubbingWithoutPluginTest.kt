package io.lokki

import io.lokki.internal.TestMock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StubbingWithoutPluginTest {
    @Test
    fun testEveryThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { every { 1 } returns 2 }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }

    @Test
    fun testCoEveryThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { coEvery { 1 } returns 2 }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }

    @Test
    fun testJustRunThrowsWithoutCompilerPlugin() {
        val exceptions = listOf(
            assertFailsWith<LokkiException> { justRun { } },
            assertFailsWith<LokkiException> { coJustRun { } },
            assertFailsWith<LokkiException> { coJustAwait { } },
        )

        assertEquals(List(3) { PLUGIN_NOT_APPLIED }, exceptions.map { it.message })
    }

    @Test
    fun testJustAwaitsThrowsWithoutCompilerPlugin() {
        val state = TestMock(null, "Repo#find()").`lokki$state`()
        val exceptions = listOf(
            assertFailsWith<LokkiException> { state.stub<Int>("Repo#find()", emptyArray()) just Awaits },
            assertFailsWith<LokkiException> { state.stub<Int>("Repo#find()", emptyArray()) returns 1 andThenJust Awaits },
        )

        assertEquals(List(2) { PLUGIN_NOT_APPLIED }, exceptions.map { it.message })
    }

    @Test
    fun testVerifyThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { verify { 1 } }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }

    @Test
    fun testCoVerifyThrowsWithoutCompilerPlugin() {
        val exceptions = listOf(
            assertFailsWith<LokkiException> { coVerify { 1 } },
            assertFailsWith<LokkiException> { coVerifyCount { 1 * { 1 } } },
            assertFailsWith<LokkiException> { coExcludeRecords { 1 } },
        )

        assertEquals(List(3) { PLUGIN_NOT_APPLIED }, exceptions.map { it.message })
    }

    @Test
    fun testVerifyCountThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { verifyCount { 1 * { 1 } } }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }

    @Test
    fun testExcludeRecordsThrowsWithoutCompilerPlugin() {
        val exception = assertFailsWith<LokkiException> { excludeRecords { 1 } }

        assertEquals(PLUGIN_NOT_APPLIED, exception.message)
    }
}
