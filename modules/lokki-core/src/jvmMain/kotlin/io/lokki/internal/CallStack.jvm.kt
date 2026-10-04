package io.lokki.internal

private val recordStackTraces = System.getProperty("lokki.recordStackTraces", "false").toBoolean()

internal actual fun captureCallStack(): Throwable? = if (recordStackTraces) Throwable() else null

internal actual fun Throwable.callStackFrames(): List<String> =
    stackTrace
        .dropWhile { it.className.startsWith("io.lokki.internal.") || "\$LokkiMock\$" in it.className }
        .dropWhile { it.methodName.endsWith("\$default") }
        .map { "${it.className}.${it.methodName}(${it.fileName ?: "Unknown Source"}:${it.lineNumber})" }
