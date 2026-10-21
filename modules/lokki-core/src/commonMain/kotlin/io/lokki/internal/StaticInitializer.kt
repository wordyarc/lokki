package io.lokki.internal

@PublishedApi
internal object StaticInitializer {
    private val threads = Threads()

    val isActive: Boolean
        get() = threads.containsCurrent()

    fun enter() {
        threads.add()
    }

    fun exit() {
        threads.remove()
    }
}

internal fun nextMark(): Long = if (StaticInitializer.isActive) 0 else nextSequenceNumber()
