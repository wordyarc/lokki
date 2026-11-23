// WITH_KOTLINX_COROUTINES
package sample

import io.lokki.Ordering
import io.lokki.coVerifyAny
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyAny

interface Player {
    fun resume()

    fun toggle()

    fun seek(position: Int): Int

    fun next(): Player

    suspend fun load(id: Int)
}

private inline fun failure(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }

fun box(): String {
    val player = mock<Player>(relaxUnitFun = true)
    player.toggle()
    verifyAny {
        player.resume()
        player.toggle()
    }
    verifyAny(exactly = 1) {
        player.resume()
        player.toggle()
    }
    verify(ordering = Ordering.ANY, atLeast = 1) {
        player.resume()
        player.toggle()
    }
    player.resume()
    verifyAny(atLeast = 2, atMost = 2) {
        player.resume()
        player.toggle()
    }
    val many = failure {
        verifyAny(exactly = 1) {
            player.resume()
            player.toggle()
        }
    } ?: return "Fail no failure on too many calls"
    if ("2 matching calls found for the declared patterns" !in many || "exactly 1 calls" !in many) return "Fail: $many"

    val silent = mock<Player>(relaxUnitFun = true)
    verifyAny(exactly = 0) { silent.resume() }
    silent.resume()
    val none = failure { verifyAny(exactly = 0) { silent.resume() } } ?: return "Fail exactly = 0"
    if ("None of the patterns should be called, but 1 matching calls found" !in none) return "Fail: $none"
    verifyAny(inverse = true) { silent.toggle() }

    val seeking = mock<Player>()
    io.lokki.every { seeking.seek(any()) } returns 0
    seeking.seek(1)
    seeking.seek(2)
    verifyAny(exactly = 2) {
        seeking.seek(any())
        seeking.seek(1)
    }
    val position = slot<Int>()
    val slotFailure = try {
        verifyAny { seeking.seek(capture(position)) }
        null
    } catch (e: io.lokki.LokkiException) {
        e.message
    } ?: return "Fail slot captured twice"
    if ("more than once" !in slotFailure) return "Fail: $slotFailure"

    val root = mock<Player>()
    io.lokki.every { root.next().seek(1) } returns 1
    verifyAny(exactly = 0) { root.next().seek(1) }
    root.next().seek(1)
    verifyAny(exactly = 1) { root.next().seek(1) }

    val suspending = mock<Player>(relaxUnitFun = true)
    io.lokki.coJustRun { suspending.load(any()) }
    coVerifyAny(exactly = 0) { suspending.load(1) }
    return "OK"
}
