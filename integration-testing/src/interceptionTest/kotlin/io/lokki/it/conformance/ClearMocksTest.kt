@file:ChainedMocks

package io.lokki.it.conformance

import io.lokki.ChainedMocks
import io.lokki.clearAllMocks
import io.lokki.clearMocks
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import io.lokki.mockStatic
import io.lokki.spy
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@ExtendWith(LokkiExtension::class)
class ClearMocksTest {
    open class MockCls {
        open fun op(a: Int) = a + 1

        open fun child(): MockCls = error("need a stub")
    }

    val spyObj = spy(MockCls())
    val mockObj = mock<MockCls>(relaxed = true)

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
    fun `clearAnswers does not affect a child`() {
        val child = mockObj.child()
        every { child.op(1) } returns 3
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }

        clearMocks(mockObj, answers = true, recordedCalls = false, childMocks = false, verificationMarks = false)

        confirmVerified(child, clear = true)
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }
        assertSame(child, mockObj.child())
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
    fun `clearRecordedCalls does not affect a child`() {
        val child = mockObj.child()
        every { child.op(1) } returns 3
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }

        clearMocks(mockObj, answers = false, recordedCalls = true, childMocks = false, verificationMarks = false)

        confirmVerified(child, clear = true)
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }
        assertSame(child, mockObj.child())
    }

    @Test
    fun clearChildMocks() {
        val child = mockObj.child()

        every { mockObj.op(1) } returns 3
        every { child.op(1) } returns 3

        assertEquals(3, mockObj.op(1))
        assertEquals(3, child.op(1))

        verify(exactly = 1) { mockObj.op(1) }
        verify(exactly = 1) { mockObj.child() }
        verify(exactly = 1) { child.op(1) }
        confirmVerified(mockObj, child)

        clearMocks(mockObj, answers = false, recordedCalls = false, childMocks = true, verificationMarks = false)

        confirmVerified(mockObj, child)

        assertEquals(3, mockObj.op(1))
        assertEquals(3, child.op(1))

        assertNotSame(child, mockObj.child())
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

    @Test
    fun `clearVerificationMarks does not affect a child`() {
        val child = mockObj.child()
        every { child.op(1) } returns 3
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }

        clearMocks(mockObj, answers = false, recordedCalls = false, childMocks = false, verificationMarks = true)

        confirmVerified(child, clear = true)
        assertEquals(3, child.op(1))
        verify(exactly = 1) { child.op(1) }
        assertSame(child, mockObj.child())
    }

    @Test
    fun clearAll() {
        val obj = spy(MockCls())
        mockStatic(::topLevelFn)

        val child = mockObj.child()

        every { spyObj.op(1) } returns 11
        every { topLevelFn() } returns 33
        every { obj.op(4) } returns 44
        every { child.op(5) } returns 55

        assertEquals(11, spyObj.op(1))
        assertEquals(33, topLevelFn())
        assertEquals(44, obj.op(4))
        assertEquals(55, child.op(5))

        clearAllMocks()

        assertEquals(2, spyObj.op(1))
        assertEquals(55, topLevelFn())
        assertEquals(5, obj.op(4))
        assertEquals(0, child.op(5))
        assertNotSame(child, mockObj.child())
    }

    @Test
    fun clearAllMocksCurrentThreadOnly() {
        var mockInOtherThread: MockCls? = null

        val thread =
            Thread {
                mockInOtherThread = mock()
                every { mockInOtherThread!!.op(any()) } returns 42
            }
        thread.start()
        thread.join()

        every { spyObj.op(any()) } returns 24
        assertEquals(24, spyObj.op(1))
        assertEquals(42, mockInOtherThread?.op(1))

        clearAllMocks()

        assertEquals(2, spyObj.op(1))
        assertEquals(42, mockInOtherThread?.op(1))
    }
}
