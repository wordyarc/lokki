package io.lokki.it.conformance

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.coVerifyCount
import io.lokki.mock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CoVerifyTest {
    open class MockCls {
        open suspend fun op(a: Int) = coroutineScope { a + 1 }
    }

    val mock = mock<MockCls>()

    private fun doCalls() {
        coEvery { mock.op(5) } returns 1
        coEvery { mock.op(6) } returns 2
        coEvery { mock.op(7) } returns 3

        runBlocking {
            assertEquals(1, mock.op(5))
            assertEquals(2, mock.op(6))
            assertEquals(3, mock.op(7))
        }
    }

    @Test
    fun checkVerify() {
        doCalls()

        coVerify {
            mock.op(6)
            mock.op(5)
        }
    }

    @Test
    fun checkVerifyInverse1() {
        doCalls()

        coVerify(inverse = true) {
            mock.op(6)
            mock.op(8)
        }
    }

    @Test
    fun checkVerifyInverse2() {
        doCalls()

        coVerify(inverse = true) {
            mock.op(4)
            mock.op(8)
        }
    }

    @Test
    fun verifyCount() {
        doCalls()

        coVerifyCount {
            0 * { mock.op(4) }
            1 * { mock.op(5) }
            (0..Int.MAX_VALUE) * { mock.op(6) }
            (1..1) * { mock.op(7) }
            (0..0) * { mock.op(8) }
            (0..1) * { mock.op(9) }
        }
    }
}
