package io.lokki.internal

internal actual inline fun <T> synchronized(lock: Any, block: () -> T): T = kotlin.synchronized(lock, block)

internal actual fun currentThread(): Any = Thread.currentThread()
