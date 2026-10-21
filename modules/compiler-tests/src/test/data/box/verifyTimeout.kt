package sample

import io.lokki.Called
import io.lokki.Ordering
import io.lokki.Runs
import io.lokki.coVerify
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify

interface Bell {
    fun ring(id: Int)
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun later(millis: Long, block: () -> Unit) {
    Thread {
        Thread.sleep(millis)
        block()
    }.start()
}

private fun message(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }

private fun bell(): Bell = mock<Bell>().also { every { it.ring(any()) } just Runs }

fun box(): String {
    val bell = bell()
    val name = "$bell"

    later(50) { bell.ring(1) }
    val start = System.nanoTime()
    verify(timeout = 5_000) { bell.ring(1) }
    if (System.nanoTime() - start > 4_000_000_000) return "Fail: waited for the whole timeout"

    val missing = message { verify(timeout = 100) { bell.ring(2) } }
    if (missing != "Verification failed: call 1 of 1: $name.ring(eq(2)). Only one matching call to $name/ring " +
        "happened, but arguments are not matching:\n[0]: argument: 1, matcher: eq(2), result: -\n\n" +
        "$CALL_SITES (timeout = 100 ms)"
    ) {
        return "Fail missing: $missing"
    }

    later(50) {
        bell.ring(3)
        bell.ring(4)
    }
    verify(Ordering.ORDERED, timeout = 5_000) {
        bell.ring(3)
        bell.ring(4)
    }
    later(50) { bell.ring(5) }
    coVerify(timeout = 5_000) { bell.ring(5) }

    val other = bell()
    later(50) { bell.ring(6) }
    val oneOfTwo = message {
        verify(timeout = 300) {
            bell.ring(6)
            other.ring(6)
        }
    }
    if (oneOfTwo != "Verification failed: call 2 of 2: $other.ring(eq(6)) was not called (timeout = 300 ms)") {
        return "Fail one of two: $oneOfTwo"
    }

    val twice = bell()
    later(20) {
        twice.ring(1)
        Thread.sleep(30)
        twice.ring(1)
    }
    val once = message { verify(exactly = 1, timeout = 300) { twice.ring(1) } }
    if (once?.startsWith("Verification failed: call 1 of 1: $twice.ring(eq(1)). 2 matching calls found") != true ||
        !once.endsWith("(timeout = 300 ms)")
    ) {
        return "Fail exactly: $once"
    }

    val quiet = bell()
    later(1_000) { quiet.ring(1) }
    verify(exactly = 0, timeout = 100) { quiet.ring(1) }
    verify(timeout = 100) { quiet wasNot Called }

    val silent = bell()
    later(30) { silent.ring(1) }
    val called = message { verify(timeout = 300) { silent wasNot Called } }
    if (called != "Verification failed: $silent should not be called:\n1) $silent.ring(1)\n\n$CALL_SITES") {
        return "Fail wasNot Called: $called"
    }

    val inverse = bell()
    later(30) { inverse.ring(1) }
    val inverted = message { verify(inverse = true, timeout = 300) { inverse.ring(1) } }
    if (inverted != "Inverse verification failed.\n\nVerified calls:\n1) $inverse.ring(1)") {
        return "Fail inverse: $inverted"
    }

    return "OK"
}
