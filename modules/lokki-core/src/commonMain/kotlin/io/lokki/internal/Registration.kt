package io.lokki.internal

import kotlin.concurrent.Volatile

@PublishedApi
internal object Registration {
    @Volatile
    private var threads = emptyArray<Any>()

    val isActive: Boolean
        get() {
            val threads = threads
            return threads.isNotEmpty() && currentThread() in threads
        }

    fun enter() {
        synchronized(this) { threads += currentThread() }
    }

    fun exit() {
        synchronized(this) {
            val index = threads.lastIndexOf(currentThread())
            threads = (threads.take(index) + threads.drop(index + 1)).toTypedArray()
        }
    }
}
