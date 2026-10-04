package io.lokki.internal

internal expect fun captureCallStack(): Throwable?

internal expect fun Throwable.callStackFrames(): List<String>
