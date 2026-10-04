package io.lokki.it.conformance

import io.lokki.clearMocks
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearMocksTest {
    interface MockCls {
        fun op(a: Int): Int
    }

    val mockObj = mock<MockCls>()

    @Test
    fun clearRecordedCalls() {
        every { mockObj.op(1) } returns 3

        assertEquals(3, mockObj.op(1))
        verify(exactly = 1) { mockObj.op(1) }

        clearMocks(mockObj, answers = false, recordedCalls = true, childMocks = false, verificationMarks = false)

        verify(inverse = true) { mockObj.op(1) }
        assertEquals(3, mockObj.op(1))
    }

    @Test
    fun clearVerificationMarks() {
        every { mockObj.op(1) } returns 3
        assertEquals(3, mockObj.op(1))
        verify { mockObj.op(1) }

        clearMocks(mockObj, answers = false, recordedCalls = false, childMocks = false, verificationMarks = true)

        verify(exactly = 1) { mockObj.op(1) }
        confirmVerified(mockObj)
        assertEquals(3, mockObj.op(1))
    }
}
