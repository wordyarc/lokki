// WITH_KOTLINX_COROUTINES

package sample

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

interface Api {
    fun compute(a: Int, block: () -> Int): Int
    fun apply(value: String, block: String.(Int) -> String): String
    fun handle(callback: Any): String
    fun both(first: () -> Int, second: () -> Int): Int
    fun optional(block: (() -> Int)?): Int
    fun run(block: suspend () -> Int): Int
    suspend fun load(id: Int, block: suspend (Int) -> String): String
}

fun MatcherScope.callback(): () -> Int = captureLambda()

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val api = mock<Api>()

    every { api.compute(1, captureLambda()) } answers { firstArg<Int>() + lambda<() -> Int>().captured() }
    if (api.compute(1) { 5 } != 6 || api.compute(1) { 7 } != 8) return "Fail lambda"
    val strict = failure { api.compute(9) { 1 } }
    if (!strict.startsWith("no answer found for Api.compute(9, lambda {})") || "compute(eq(1), slotCapture())" !in strict) {
        return "Fail strict: $strict"
    }

    every { api.compute(2, callback()) } answers { lambda<() -> Int>().captured() * 10 }
    if (api.compute(2) { 3 } != 30) return "Fail helper"

    every { api.apply(any(), captureLambda()) } answers { lambda<String.(Int) -> String>().captured(firstArg(), 2) }
    if (api.apply("a") { this + it } != "a2") return "Fail lambda with receiver"

    every { api.handle(captureLambda<(Int) -> String>()) } answers { lambda<(Int) -> String>().captured(3) }
    every { api.handle(captureLambda<() -> String>()) } answers { lambda<() -> String>().captured() + "!" }
    if (api.handle({ n: Int -> "n$n" }) != "n3") return "Fail typed (Int) -> String"
    if (api.handle({ "zero" }) != "zero!") return "Fail typed () -> String"
    if (!failure { api.handle("text") }.startsWith("no answer found for Api.handle(text)")) return "Fail typed: not a function"

    every { api.both(captureLambda(), captureLambda()) } answers { lambda<() -> Int>().captured() }
    if (api.both({ 1 }, { 2 }) != 2) return "Fail: the last captureLambda() is used"

    every { api.optional(captureLambda()) } returns 1
    if (api.optional { 0 } != 1) return "Fail nullable parameter"
    if (!failure { api.optional(null) }.startsWith("no answer found for Api.optional(null)")) return "Fail: null is captured"

    every { api.compute(3, any()) } answers { lambda<() -> Int>().captured() }
    val notCaptured = failure { api.compute(3) { 3 } }
    if (notCaptured != "lambda() in the answer of Api.compute(3, lambda {}) needs captureLambda() as an argument of the stubbed call") {
        return "Fail lambda() without captureLambda(): $notCaptured"
    }

    verify(exactly = 2) { api.compute(1, captureLambda()) }

    every { api.run(captureCoroutine()) } coAnswers { coroutine<suspend () -> Int>().captured() + 1 }
    if (api.run { 41 } != 42) return "Fail captureCoroutine of non-suspend member"

    coEvery { api.load(any(), captureCoroutine()) } coAnswers {
        yield()
        coroutine<suspend (Int) -> String>().captured(firstArg())
    }
    coEvery { api.load(5, captureLambda()) } coAnswers { lambda<suspend (Int) -> String>().captured(50) }
    val loaded = runBlocking {
        val first = async { api.load(1) { "first $it" } }
        val second = async { api.load(2) { "second $it" } }
        listOf(first.await(), second.await(), api.load(5) { "fifth $it" })
    }
    if (loaded != listOf("first 1", "second 2", "fifth 50")) return "Fail: each call gets its own callback $loaded"
    every { api.run(any()) } answers { coroutine<suspend () -> Int>().hashCode() }
    val runFailure = failure { api.run { 0 } }
    if (runFailure != "coroutine() in the answer of Api.run(lambda {}) needs captureCoroutine() as an argument of the stubbed call") {
        return "Fail coroutine() without captureCoroutine(): $runFailure"
    }

    coVerify { api.load(1, captureCoroutine()) }

    return "OK"
}
