package io.lokki.internal

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

internal actual inline fun <T> synchronized(lock: Any, block: () -> T): T = kotlin.synchronized(lock, block)

internal actual fun currentThread(): Any = Thread.currentThread()

private val sequenceNumbers = AtomicLong()

internal actual fun nextSequenceNumber(): Long = sequenceNumbers.incrementAndGet()

internal actual fun nanoTime(): Long = System.nanoTime()

internal actual fun sleep(millis: Long) {
    if (millis > 0) Thread.sleep(millis)
}

internal actual fun park(nanos: Long) {
    LockSupport.parkNanos(nanos)
}

internal actual fun unpark(thread: Any) {
    LockSupport.unpark(thread as Thread)
}
