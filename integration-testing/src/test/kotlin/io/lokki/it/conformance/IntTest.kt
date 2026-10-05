package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class IntTest {
    @Test
    fun numberUnboxing() {
        val mock: Number = mock()

        every { mock.toInt() } returns 3

        assertEquals(3, mock.toInt())
    }
}
