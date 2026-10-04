package io.lokki.it.conformance

import io.lokki.CapturingSlot
import io.lokki.every
import io.lokki.mock
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.test.Test

class CallableTest {
    @Test
    fun test() {
        val taskCaptor =
            CapturingSlot<Callable<Boolean>>().apply {
                captured = Callable { true }
            }

        val asyncTaskExecutor =
            mock<ExecutorService> {
                every { submit(capture(taskCaptor)) } answers {
                    CompletableFuture.completedFuture(taskCaptor.captured.call())
                }
            }

        asyncTaskExecutor.submit(Callable { true })
    }
}
