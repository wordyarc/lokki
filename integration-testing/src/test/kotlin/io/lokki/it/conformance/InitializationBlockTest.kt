package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertEquals

class InitializationBlockTest {
    @Test
    fun initializeMockWithInitializationBlock() {
        val mock: MockCls =
            mock {
                every { op(1, 2) } returns 5
            }

        assertEquals(5, mock.op(1, 2))
    }

    @Test
    fun initializeSpyWithInitializationBlock() {
        val mock: MockCls =
            spy {
                every { op(1, 2) } returns 5
            }

        assertEquals(5, mock.op(1, 2))
        assertEquals(4, mock.op(2, 2))
    }

    open class MockCls {
        open fun op(
            a: Int,
            b: Int,
        ) = a + b
    }
}
