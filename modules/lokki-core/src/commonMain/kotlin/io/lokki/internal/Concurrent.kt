package io.lokki.internal

internal expect inline fun <T> synchronized(lock: Any, block: () -> T): T

internal expect fun currentThread(): Any
