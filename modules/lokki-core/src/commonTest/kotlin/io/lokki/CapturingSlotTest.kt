package io.lokki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapturingSlotTest {
    @Test
    fun testValueIsNotAvailableBeforeCapture() {
        val slot = slot<String>()

        assertFalse(slot.isCaptured)
        assertFalse(slot.isNull)
        assertEquals("slot()", slot.toString())
        assertEquals("Value not yet captured.", assertFailsWith<IllegalStateException> { slot.captured }.message)
    }

    @Test
    fun testCapturedValueIsKeptUntilCleared() {
        val slot = slot<String?>()

        slot.captured = "a"
        assertTrue(slot.isCaptured)
        assertEquals("a", slot.captured)
        assertEquals("slot(captured=a)", slot.toString())

        slot.captured = null
        assertTrue(slot.isCaptured && slot.isNull)
        assertEquals(null, slot.captured)

        slot.clear()
        assertFalse(slot.isCaptured)
    }
}
