package io.lokki.internal

import kotlin.concurrent.Volatile

@PublishedApi
internal object Registration {
    private val registering = Threads()
    private val chains = Threads()

    val isActive: Boolean
        get() = registering.containsCurrent()

    val isChain: Boolean
        get() = chains.containsCurrent()

    fun enter() {
        registering.add()
    }

    fun exit() {
        registering.remove()
    }

    fun enterChain() {
        chains.add()
    }

    fun exitChain() {
        chains.remove()
    }
}

private class Threads {
    @Volatile
    private var threads = emptyArray<Any>()

    fun containsCurrent(): Boolean {
        val threads = threads
        return threads.isNotEmpty() && currentThread() in threads
    }

    fun add() {
        synchronized(this) { threads += currentThread() }
    }

    fun remove() {
        synchronized(this) {
            val index = threads.lastIndexOf(currentThread())
            threads = (threads.take(index) + threads.drop(index + 1)).toTypedArray()
        }
    }
}
