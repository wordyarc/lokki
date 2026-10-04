package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class NullsTest {
    interface Wrapper

    data class IntWrapper(
        val data: Int,
    ) : Wrapper

    interface MockCls {
        fun op(
            a: Wrapper?,
            b: Wrapper?,
        ): Int?
    }

    val mock = mock<MockCls>()

    @Test
    fun isNull() {
        every { mock.op(null, isNull()) } returns 4

        assertEquals(4, mock.op(null, null))

        verify { mock.op(isNull(), null) }
    }

    @Test
    fun returnsNull() {
        every { mock.op(IntWrapper(1), IntWrapper(2)) } returns null

        assertEquals(null, mock.op(IntWrapper(1), IntWrapper(2)))

        verify { mock.op(IntWrapper(1), IntWrapper(2)) }
    }
}
