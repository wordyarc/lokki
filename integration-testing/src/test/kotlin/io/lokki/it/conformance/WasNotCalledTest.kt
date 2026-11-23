package io.lokki.it.conformance

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WasNotCalledTest {
    val mock = mock<MockCls>()

    @Test
    fun wasNotCalledOnPlainValueStillThrows() {
        assertFailsWith<LokkiException>("was not can should throw LokkiException on non mock object") {
            verify { "a plain value" wasNot Called }
        }
    }

    @Test
    fun wasNotCalledOnMethodCallResult() {
        verify { mock.flowOp(1, 2) wasNot Called }
    }

    @Test
    fun wasNotCalledOnMethodCallResultShouldThrowAssertionErrorIfMethodHasBeenCalled() {
        every { mock.flowOp(1, 2) } returns flowOf(1, 2)

        mock.flowOp(1, 2)

        assertFailsWith<AssertionError> {
            verify { mock.flowOp(1, 2) wasNot Called }
        }
    }

    @Test
    fun wasNotCalledOnMethodCallResultWithAnyMatcher() {
        verify { mock.flowOp(any(), any()) wasNot Called }
    }

    @Test
    fun wasNotCalledOnMethodCallResultWithAnyMatcherShouldThrowAssertionErrorIfMethodHasBeenCalled() {
        every { mock.flowOp(any(), any()) } returns flowOf(1, 2)

        mock.flowOp(1, 2)

        assertFailsWith<AssertionError> {
            verify { mock.flowOp(any(), any()) wasNot Called }
        }
    }

    @Test
    fun wasNotCalledOnMockedObject() {
        verify { mock wasNot Called }
    }

    @Test
    fun wasNotCalledShouldThrowAssertionErrorIfMockHasBeenCalled() {
        assertFailsWith<AssertionError>("was not can should throw AssertionError if mock has been called") {
            every { mock.flowOp(1, 2) } returns flowOf(1, 2)

            mock.flowOp(1, 2)
            verify { mock wasNot Called }
        }
    }

    interface MockCls {
        fun flowOp(
            a: Int = 1,
            b: Int = 2,
        ): Flow<Int>
    }
}
