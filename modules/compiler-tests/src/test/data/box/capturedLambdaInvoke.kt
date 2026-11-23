// WITH_KOTLINX_COROUTINES
package sample

import io.lokki.coInvoke
import io.lokki.every
import io.lokki.invoke
import io.lokki.mock
import io.lokki.slot
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

typealias Long22 = (
    Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int,
) -> Int

interface Api {
    fun call(callback: (Int) -> String): String

    fun wide(callback: Long22): Int

    suspend fun load(callback: suspend (Int) -> String): String
}

private fun run(block: suspend () -> Unit) {
    block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })
}

fun box(): String {
    val api = mock<Api>()
    every { api.call(captureLambda()) } answers { lambda<(Int) -> String>().invoke(5) }
    if (api.call { "got $it" } != "got 5") return "Fail invoke"

    every { api.wide(captureLambda()) } answers {
        lambda<Long22>().invoke(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)
    }
    val sum = api.wide { a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v ->
        a + b + c + d + e + f + g + h + i + j + k + l + m + n + o + p + q + r + s + t + u + v
    }
    if (sum != 253) return "Fail invoke with 22 arguments: $sum"

    io.lokki.coEvery { api.load(captureCoroutine()) } coAnswers {
        coroutine<suspend (Int) -> String>().coInvoke(7)
    }
    var resume: Continuation<String>? = null
    var result = ""
    run { result = api.load { value -> suspendCoroutine<String> { resume = it } + value } }
    if (result != "") return "Fail coInvoke did not suspend: $result"
    resume!!.resumeWith(Result.success("late"))
    if (result != "late7") return "Fail coInvoke result: $result"

    val slot = slot<() -> String>()
    slot.captured = { "plain" }
    return if (slot.invoke() == "plain") "OK" else "Fail slot"
}
