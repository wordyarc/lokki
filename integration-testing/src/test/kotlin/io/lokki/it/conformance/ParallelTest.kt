package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import java.util.Collections.synchronizedList
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ParallelTest {
    interface MockCls {
        fun op(
            a: Int,
            b: Int,
        ): Int
    }

    data class Op(
        val mock: MockCls,
        val a: Int,
        val b: Int,
    )

    val mock1 = mock<MockCls>()
    val mock2 = mock<MockCls>()
    val mock3 = mock<MockCls>()
    val mock4 = mock<MockCls>()
    val mock5 = mock<MockCls>()
    val mock6 = mock<MockCls>()

    @Test
    fun test() {
        every { mock1.op(any(), any()) } answers { firstArg<Int>() - secondArg<Int>() }
        every { mock2.op(any(), any()) } answers { firstArg<Int>() * secondArg<Int>() }
        every { mock3.op(any(), any()) } answers { firstArg<Int>() / secondArg<Int>() }
        every { mock4.op(any(), any()) } answers { firstArg<Int>() xor secondArg() }
        every { mock5.op(any(), any()) } answers { firstArg<Int>() and secondArg() }
        every { mock6.op(any(), any()) } answers { firstArg<Int>() or secondArg() }

        val rnd = Random()
        val threads = mutableListOf<Thread>()
        val failures = synchronizedList(mutableListOf<Throwable>())

        repeat(30) {
            Thread {
                val mocks = listOf(mock1, mock2, mock3, mock4, mock5, mock6)
                try {
                    val ops = mutableListOf<Op>()
                    repeat(500) {
                        val mock = mocks[rnd.nextInt(mocks.size)]
                        val a = rnd.nextInt(100) + 5
                        val b = rnd.nextInt(100) + 5
                        mock.op(a, b)
                        ops.add(Op(mock, a, b))
                    }

                    repeat(500) {
                        val op = ops[rnd.nextInt(ops.size)]
                        verify { op.mock.op(op.a, op.b) }
                    }
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
            }.apply { start() }
                .apply { threads.add(this) }
        }

        threads.forEach { it.join() }

        assertEquals(emptyList(), failures)
    }
}
