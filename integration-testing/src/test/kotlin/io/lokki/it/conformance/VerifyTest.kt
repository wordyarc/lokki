package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyCount
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifyTest {
    val mock = mock<MockCls>()

    private fun doCalls() {
        every { mock.op(5) } returns 1
        every { mock.op(6) } returns 2
        every { mock.op(7) } returns 3

        assertEquals(1, mock.op(5))
        assertEquals(2, mock.op(6))
        assertEquals(3, mock.op(7))
    }

    @Test
    fun checkVerify() {
        doCalls()

        verify {
            mock.op(6)
            mock.op(5)
        }
    }

    @Test
    fun checkVerifyInverse1() {
        doCalls()

        verify(inverse = true) {
            mock.op(6)
            mock.op(8)
        }
    }

    @Test
    fun checkVerifyInverse2() {
        doCalls()

        verify(inverse = true) {
            mock.op(4)
            mock.op(8)
        }
    }

    @Test
    fun verifyCount() {
        doCalls()

        verifyCount {
            0 * { mock.op(4) }
            1 * { mock.op(5) }
            (0..Int.MAX_VALUE) * { mock.op(6) }
            (1..1) * { mock.op(7) }
            (0..0) * { mock.op(8) }
            (0..1) * { mock.op(9) }
        }
    }

    @Test
    fun verifyWithToString() {
        val foo = mock<Foo>()
        val bar = mock<Bar>()

        every { bar.baz("$foo") } just Runs

        bar.baz("$foo")

        verify(exactly = 1) { bar.baz("$foo") }
    }

    interface Bar {
        fun baz(foo: String)
    }

    interface Foo

    interface MockCls {
        fun op(a: Int): Int
    }
}
