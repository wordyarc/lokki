package io.lokki.internal

internal expect inline fun <T> synchronized(lock: Any, block: () -> T): T

internal expect fun currentThread(): Any

internal expect fun nextSequenceNumber(): Long

internal expect fun nanoTime(): Long

internal expect fun sleep(millis: Long)

internal expect fun park(nanos: Long)

internal expect fun unpark(thread: Any)

internal expect class CommonThreadLocal<T>() {
    fun get(): T

    fun set(value: T)
}

internal expect class WeakReference<T : Any>(referent: T) {
    fun get(): T?
}
