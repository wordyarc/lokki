package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentStubInvocationTest {
    @Test
    fun testConcurrentGetMany() {
        val numValues = 10000
        val nThreads = 10
        val fetchTimes = numValues / nThreads

        open class ClassToMock {
            open fun getValue(): Int = 0
        }

        val mock = mock<ClassToMock>(relaxed = true)
        val values = (1..numValues).toList()
        val sum = AtomicInteger()

        every { mock.getValue() } returnsMany values

        val latch = CountDownLatch(nThreads)
        (1..nThreads)
            .map {
                thread {
                    latch.countDown()
                    latch.await()

                    repeat(fetchTimes) {
                        sum.addAndGet(mock.getValue())
                    }
                }
            }.forEach {
                it.join()
            }

        assertEquals(values.sum(), sum.get())
    }
}
