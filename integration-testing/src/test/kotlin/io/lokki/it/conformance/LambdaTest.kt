package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class LambdaTest {
    private val mock = mock<MockCls>()

    @Test
    fun simpleLambdaTest() {
        every {
            mock.lambdaOp(1, captureLambda())
        } answers { 1 - lambda<() -> Int>().captured() }

        assertEquals(-4, mock.lambdaOp(1) { 5 })

        verify {
            mock.lambdaOp(1, any())
        }
    }

    open class MockCls {
        open fun lambdaOp(
            a: Int,
            b: () -> Int,
        ) = a + b()
    }
}
