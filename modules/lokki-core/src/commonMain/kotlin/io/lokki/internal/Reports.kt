package io.lokki.internal

internal fun List<CallRecord>.numbered(): String =
    withIndex().joinToString("\n") { (index, call) -> "${index + 1}) $call" }

internal fun List<CallRecord>.callSites(): String {
    if (all { it.callStack == null }) return "\n$CALL_SITES_NOT_RECORDED"
    return withIndex().joinToString("\n\n", prefix = "\nStack traces:\n") { (index, call) ->
        val number = "${index + 1}) "
        number + call.frames().joinToString("\n" + " ".repeat(number.length))
    }
}

internal fun CallRecord.callSite(): String =
    if (callStack == null) "\n$CALL_SITES_NOT_RECORDED" else "\nStack trace:\n" + frames().joinToString("\n")

internal fun List<CallRecord>.unansweredReport(): String =
    if (isEmpty()) "" else "Calls without an answer threw LokkiException:\n${numbered()}\n\n"

private fun CallRecord.frames(): List<String> = callStack?.callStackFrames().orEmpty()

private const val CALL_SITES_NOT_RECORDED = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"
