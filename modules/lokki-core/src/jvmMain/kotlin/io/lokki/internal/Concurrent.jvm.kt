package io.lokki.internal

import java.util.concurrent.atomic.AtomicLong

internal actual inline fun <T> synchronized(lock: Any, block: () -> T): T = kotlin.synchronized(lock, block)

internal actual fun currentThread(): Any = Thread.currentThread()

private val sequenceNumbers = AtomicLong()

internal actual fun nextSequenceNumber(): Long = sequenceNumbers.incrementAndGet()
