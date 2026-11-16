package io.lokki.it.conformance

import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mockStatic
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
class StaticMockTest {
    @Test
    fun simpleStaticMock() {
        mockStatic(Int::op)

        every { 5 op 6 } returns 2

        assertEquals(2, 5 op 6)

        verify { 5 op 6 }
    }

    @Test
    fun clearStateStaticMock() {
        mockStatic(Int::op)

        every { 5 op 6 } returns 2

        assertEquals(2, 5 op 6)

        verify { 5 op 6 }

        mockStatic(Int::op)

        verify(exactly = 0) { 5 op 6 }

        assertEquals(11, 5 op 6)

        every { 5 op 6 } returns 3

        assertEquals(3, 5 op 6)

        verify { 5 op 6 }
    }
}
