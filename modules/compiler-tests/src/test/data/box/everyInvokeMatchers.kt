package sample

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

typealias Wide = (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> Unit

interface Worker {
    fun run(task: () -> Unit)
    fun map(value: Int, transform: (Int) -> String): Int
    fun submit(callback: (String, Int) -> Unit): Boolean
    fun maybe(callback: (() -> Unit)?): Int
    fun value(value: Any?): String
    fun wide(callback: Wide): Int
    fun later(callback: suspend (Int) -> Unit): Int
    suspend fun load(callback: suspend (Int) -> Unit): Int
}

fun MatcherScope.withAnswer(): (Int) -> String = invoke(42)

fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val worker = mock<Worker>()

    var runs = 0
    every { worker.run(invoke()) } just Runs
    worker.run { runs++ }
    if (runs != 1) return "Fail: invoke()"

    val seen = mutableListOf<Int>()
    every { worker.map(any(), invoke(42)) } returns 1
    if (worker.map(1) { seen += it; "x" } != 1 || seen != listOf(42)) return "Fail: invoke(arg): $seen"

    val submitted = mutableListOf<String>()
    every { worker.submit(invoke("a", 2)) } returns true
    if (!worker.submit { name, count -> submitted += name.repeat(count) } || submitted != listOf("aa")) {
        return "Fail: invoke(arg1, arg2): $submitted"
    }

    every { worker.maybe(invoke()) } returns 3
    if (worker.maybe(null) != 3) return "Fail: null argument"

    every { worker.value(invoke<Int, Unit>(5)) } returns "function"
    var called = 0
    if (worker.value({ value: Int -> called += value }) != "function" || called != 5) return "Fail: typed invoke"
    val notFunction = failure { worker.value("text") }
    if (!notFunction.startsWith("no answer found") || "Worker.value(invoke(5))" !in notFunction) {
        return "Fail: description of invoke: $notFunction"
    }

    var sum = 0
    every { worker.wide(invoke(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)) } returns 4
    val wide: Wide = { a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20, a21, a22 ->
        sum = a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15 + a16 + a17 + a18 + a19 +
            a20 + a21 + a22
    }
    if (worker.wide(wide) != 4 || sum != 253) return "Fail: invoke with 22 arguments: $sum"

    seen.clear()
    every { worker.map(7, withAnswer()) } returns 5
    if (worker.map(7) { seen += it; "y" } != 5 || seen != listOf(42)) return "Fail: invoke of a helper: $seen"

    seen.clear()
    verify { worker.map(7, invoke(8)) }
    if (seen != listOf(8)) return "Fail: invoke in verify: $seen"

    val loaded = mutableListOf<Int>()
    every { worker.later(coInvoke(6)) } returns 6
    if (worker.later { loaded += it } != 6 || loaded != listOf(6)) return "Fail: coInvoke of a non-suspend member: $loaded"

    coEvery { worker.load(coInvoke(9)) } returns 7
    if (complete { worker.load { loaded += it } } != 7 || loaded != listOf(6, 9)) return "Fail: coInvoke: $loaded"

    val suspended = failure { worker.later { suspendCoroutine<Unit> { } } }
    if (suspended != "coInvoke suspended; a matcher must complete without suspension") {
        return "Fail: suspension in coInvoke: $suspended"
    }

    return "OK"
}
