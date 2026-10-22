package io.lokki.it.conformance

import io.lokki.clearStaticMock
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mockStatic
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(LokkiExtension::class)
class StaticMockkTest {
    @Test
    fun extensionFunctionStaticMock() {
        mockStatic(Int::op)

        every { 5 op 6 } returns 2

        assertEquals(2, 5 op 6)

        verify { 5 op 6 }
    }

    @Test
    fun extensionFunctionClearStateStaticMock() {
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

    @Test
    fun extensionFunctionClearStaticMock() {
        mockStatic(Int::op)

        every { 5 op 6 } returns 2

        assertEquals(2, 5 op 6)

        clearStaticMock(Int::op)

        verify(exactly = 0) { 5 op 6 }
    }

    @Test
    fun extensionPropertyStaticMock() {
        mockStatic(Int::selfOp)

        every { 5.selfOp } returns 2

        assertEquals(2, 5.selfOp)

        verify { 5.selfOp }
    }

    @Test
    fun extensionPropertyClearStateStaticMock() {
        mockStatic(Int::selfOp)

        every { 5.selfOp } returns 2

        assertEquals(2, 5.selfOp)

        verify { 5.selfOp }

        mockStatic(Int::selfOp)

        verify(exactly = 0) { 5.selfOp }

        assertEquals(25, 5.selfOp)

        every { 5.selfOp } returns 3

        assertEquals(3, 5.selfOp)

        verify { 5.selfOp }
    }

    @Test
    fun extensionPropertyClearStaticMock() {
        mockStatic(Int::selfOp)

        every { 5.selfOp } returns 2

        assertEquals(2, 5.selfOp)

        clearStaticMock(Int::selfOp)

        verify(exactly = 0) { 5.selfOp }
    }

    @Test
    fun confirmVerifiedToWorkWithStaticMockFunction() {
        mockStatic(Int::oper)
        every { 5 oper 6 } returns 2

        assertEquals(2, 5 oper 6)

        verify(exactly = 1) { 5 oper 6 }
        confirmVerified(Int::oper)
    }

    @Test
    fun confirmVerifiedToWorkWithStaticMockFunctionAndClear() {
        mockStatic(Int::oper)
        every { 5 oper 6 } returns 2

        assertEquals(2, 5 oper 6)

        verify(exactly = 1) { 5 oper 6 }
        confirmVerified(Int::oper, clear = true)

        verify(exactly = 0) { 5 oper 6 }
    }

    @Test
    fun confirmVerifiedToWorkWithStaticMockProperty() {
        mockStatic(Int::selfOp)
        every { 5.selfOp } returns 2

        assertEquals(2, 5.selfOp)

        verify(exactly = 1) { 5.selfOp }
        confirmVerified(Int::selfOp)
    }

    @Test
    fun confirmVerifiedToWorkWithStaticMockPropertyAndClear() {
        mockStatic(Int::selfOp)
        every { 5.selfOp } returns 2

        assertEquals(2, 5.selfOp)

        verify(exactly = 1) { 5.selfOp }
        confirmVerified(Int::selfOp, clear = true)

        verify(exactly = 0) { 5.selfOp }
    }

    @Test
    fun confirmVerifiedFunctionClearKeepsSiblingCalls() {
        mockStatic(Int::oper)
        mockStatic(Int::selfOp)
        every { 5 oper 6 } returns 2
        every { 5.selfOp } returns 99

        assertEquals(2, 5 oper 6)
        assertEquals(99, 5.selfOp)

        verify(exactly = 1) { 5 oper 6 }
        confirmVerified(Int::oper, clear = true)

        verify(exactly = 0) { 5 oper 6 }
        assertFailsWith<AssertionError> {
            confirmVerified(Int::selfOp)
        }
    }

    @Test
    fun confirmVerifiedKFunctionIsScopedToSelectedFunction() {
        mockStatic(Int::oper)
        mockStatic(Int::selfOp)
        every { 5 oper 6 } returns 2
        every { 5.selfOp } returns 99

        assertEquals(2, 5 oper 6)
        assertEquals(99, 5.selfOp)

        verify(exactly = 1) { 5 oper 6 }
        confirmVerified(Int::oper)

        assertFailsWith<AssertionError> {
            confirmVerified(Int::selfOp)
        }
    }
}
