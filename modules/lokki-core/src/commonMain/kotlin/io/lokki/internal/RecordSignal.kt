package io.lokki.internal

import kotlin.concurrent.Volatile

internal class RecordSignal {
    private val waiter = currentThread()

    @Volatile
    private var signaled = false

    fun signal() {
        signaled = true
        unpark(waiter)
    }

    fun await(deadline: Long): Boolean {
        while (!signaled) {
            val remaining = deadline - nanoTime()
            if (remaining <= 0) return false
            park(remaining)
        }
        signaled = false
        return true
    }
}
