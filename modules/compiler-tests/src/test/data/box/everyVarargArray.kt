package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Log {
    fun write(vararg lines: String): Int
}

fun box(): String {
    val log = mock<Log>()
    every { log.write(*varargArray { it.size == 2 && it[0] == "a" }) } returns 1
    every { log.write(*varargArray<String> { it.isEmpty() }) } returns 2
    if (log.write("a", "b") != 1) return "Fail predicate"
    if (log.write() != 2) return "Fail empty"
    verify(exactly = 1) { log.write(*varargArray { it.contentEquals(arrayOf("a", "b")) }) }
    return try {
        log.write("b", "a")
        "Fail unmatched"
    } catch (_: io.lokki.LokkiException) {
        "OK"
    }
}
