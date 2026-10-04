package io.lokki

import io.lokki.internal.CallRecord
import io.lokki.internal.TestMock
import io.lokki.internal.callSite
import io.lokki.internal.callSites
import io.lokki.internal.captureCallStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallSitesTest {
    private val state = TestMock("repo").`lokki$state`

    @Test
    fun testCallStacksAreNotCapturedByDefault() {
        assertNull(captureCallStack())
    }

    @Test
    fun testCallSitesStartAtTheCaller() {
        val call = CallRecord(state, 0, arrayOf(null), 1, Throwable())
        val caller = "io.lokki.CallSitesTest.testCallSitesStartAtTheCaller(CallSitesTest.kt:"

        val sites = listOf(call, call).callSites().lines()

        assertEquals(listOf("", "Stack traces:"), sites.take(2))
        assertTrue(sites[2].startsWith("1) $caller"), sites[2])
        assertTrue(sites[3].startsWith("   ") && !sites[3].startsWith("    "), sites[3])
        assertTrue("2) $caller" in sites.map { it.substringBefore("CallSitesTest.kt:") + "CallSitesTest.kt:" })
        assertTrue(call.callSite().startsWith("\nStack trace:\n$caller"))
    }
}
