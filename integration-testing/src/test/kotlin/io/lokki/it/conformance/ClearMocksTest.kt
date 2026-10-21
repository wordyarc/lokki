package io.lokki.it.conformance

import io.lokki.clearMocks
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.spy
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearMocksTest {
    open class MockCls {
        open fun op(a: Int) = a + 1
    }

    val spyObj = spy(MockCls())

    @Test
    fun clearAnswers() {
        every { spyObj.op(1) } returns 3
        assertEquals(3, spyObj.op(1))
        verify(exactly = 1) { spyObj.op(1) }

        clearMocks(spyObj, answers = true, recordedCalls = false, childMocks = false, verificationMarks = false)

        confirmVerified(spyObj, clear = true)
        assertEquals(2, spyObj.op(1))
        verify(exactly = 1) { spyObj.op(1) }
    }

    @Test
    fun clearRecordedCalls() {
        every { spyObj.op(1) } returns 3

        assertEquals(3, spyObj.op(1))
        verify(exactly = 1) { spyObj.op(1) }

        clearMocks(spyObj, answers = false, recordedCalls = true, childMocks = false, verificationMarks = false)

        verify(inverse = true) { spyObj.op(1) }
        assertEquals(3, spyObj.op(1))
    }

    @Test
    fun clearVerificationMarks() {
        every { spyObj.op(1) } returns 3
        assertEquals(3, spyObj.op(1))
        verify { spyObj.op(1) }

        clearMocks(spyObj, answers = false, recordedCalls = false, childMocks = false, verificationMarks = true)

        verify(exactly = 1) { spyObj.op(1) }
        confirmVerified(spyObj)
        assertEquals(3, spyObj.op(1))
    }
}
