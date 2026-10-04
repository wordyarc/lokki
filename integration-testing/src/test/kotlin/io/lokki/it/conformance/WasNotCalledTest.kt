package io.lokki.it.conformance

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WasNotCalledTest {
    val mock = mock<MockCls>()

    @Test
    fun wasNotCalledOnNonMockedObject() {
        assertFailsWith<LokkiException>("was not can should throw LokkiException on non mock object") {
            @Suppress("LOKKI_WAS_NOT_CALLED_ON_CALL_RESULT")
            verify { mock.listOp(1, 2) wasNot Called }
        }
    }

    @Test
    fun wasNotCalledOnMockedObject() {
        verify { mock wasNot Called }
    }

    @Test
    fun wasNotCalledShouldThrowAssertionErrorIfMockHasBeenCalled() {
        assertFailsWith<AssertionError>("was not can should throw AssertionError if mock has been called") {
            every { mock.listOp(1, 2) } returns listOf(1, 2)

            mock.listOp(1, 2)
            verify { mock wasNot Called }
        }
    }

    interface MockCls {
        fun listOp(
            a: Int = 1,
            b: Int = 2,
        ): List<Int>
    }
}
