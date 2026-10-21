package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceConditionInMockCreationTest {
    open class TestCls {
        open fun fn() = "abc"
    }

    @Test
    fun testRaceCondition() {
        val n = 10
        val cyclicBarrier = CyclicBarrier(n)
        val latch = CountDownLatch(n)
        val result = AtomicInteger()
        val errors = AtomicInteger()
        repeat(n) { i ->
            thread {
                try {
                    cyclicBarrier.await()
                    val mock = mock<TestCls>()
                    every { mock.fn() } returns "def $i"
                    if (mock.fn() == "def $i") {
                        result.incrementAndGet()
                    }
                } catch (ex: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        assertEquals(0, errors.get(), "error count")
        assertEquals(n, result.get(), "result count")
    }
}
