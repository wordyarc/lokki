package io.lokki.it.conformance

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.mock
import io.lokki.slot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class CoroutinesTest {
    private val mock = mock<MockCls>()

    @Test
    fun simpleCoroutineCall() {
        coEvery { mock.coOtherOp(1, any()) } answers { 2 + firstArg<Int>() }

        runBlocking {
            mock.coOtherOp(1, 2)
        }

        coVerify { mock.coOtherOp(1, 2) }
    }

    @Test
    fun coroutineLambdaSlot() {
        val slot = slot<suspend () -> Int>()
        coEvery { mock.coLambdaOp(1, capture(slot)) } coAnswers {
            1 - slot.captured()
        }

        runBlocking {
            mock.coLambdaOp(1) { 2 }
        }

        coVerify {
            mock.coLambdaOp(1, any())
        }
    }

    open class MockCls {
        open suspend fun coOtherOp(
            a: Int = 1,
            b: Int = 2,
        ): Int = coroutineScope { a + b }

        open suspend fun coLambdaOp(
            a: Int,
            b: suspend () -> Int,
        ) = a + b()
    }
}
