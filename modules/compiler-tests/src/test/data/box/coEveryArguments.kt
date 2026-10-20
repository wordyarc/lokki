package sample

import io.lokki.MatcherScope
import io.lokki.coEvery
import io.lokki.coJustRun
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface Api {
    suspend fun load(id: Int): String
    suspend fun save(id: Int, name: String): Int
    suspend fun reset(id: Int, reason: String)
    suspend fun next(): Int
    fun size(id: Int): Int
}

private val log = ArrayList<String>()
private val waiting = ArrayList<() -> Unit>()

suspend fun <T> logged(label: String, value: T): T {
    log += label
    return value
}

suspend fun suspended(value: Int): Int = suspendCoroutine { continuation ->
    log += "suspended"
    waiting += { continuation.resume(value) }
}

suspend fun failing(): Int = throw IllegalStateException("failing")

suspend fun MatcherScope.atLeast(value: Int): Int = more(logged("helper", value), andEquals = true)

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    while (outcome == null) waiting.removeAt(0)()
    return outcome!!
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: Exception) {
        "${e::class.simpleName}: ${e.message}"
    }

suspend fun stubInside(api: Api) {
    coEvery { api.load(logged("inside", 7)) } returns "seven"
}

fun box(): String {
    val api = mock<Api>()

    coEvery { logged("receiver", api).save(logged("id", 1), logged("name", "a")) } returns 1
    if (log != listOf("receiver", "id", "name")) return "Fail order: $log"
    if (complete { api.save(1, "a") }.getOrNull() != 1) return "Fail stub with suspend calls"

    log.clear()
    coEvery { api.save(name = logged("name", "b"), id = logged("id", 2)) } returns 2
    if (log != listOf("name", "id")) return "Fail order of named arguments: $log"
    if (complete { api.save(2, "b") }.getOrNull() != 2) return "Fail named arguments"

    log.clear()
    coEvery { api.save(any(), eq(logged("eq", "c"))) } returns 3
    coEvery { api.load(atLeast(10)) } returns "many"
    if (log != listOf("eq", "helper")) return "Fail matcher arguments: $log"
    if (complete { listOf(api.save(5, "c"), api.load(10), api.load(11)) }.getOrNull() != listOf(3, "many", "many")) {
        return "Fail matchers with suspend calls"
    }

    val ids = mock<Api>()
    coEvery { ids.next() } returnsMany listOf(5, 6, 7)
    coEvery { api.load(ids.next()) } returns "five"
    if (complete { ids.next() }.getOrNull() != 5) return "Fail: registration moved the answers"
    if (complete { api.load(5) }.getOrNull() != "five") return "Fail stub with a mock in the argument"

    coEvery { api.size(logged("size", 4)) } returns 40
    if (api.size(4) != 40) return "Fail non-suspend member"

    coJustRun { api.reset(logged("reset", 1), any()) }
    if (complete { api.reset(1, "r") }.isFailure) return "Fail coJustRun"

    if (complete { stubInside(api); api.load(7) }.getOrNull() != "seven") return "Fail coEvery in a suspend function"

    val thrown = failure { coEvery { api.load(failing()) } returns "failed" }
    if (thrown != "IllegalStateException: failing") return "Fail exception: $thrown"

    log.clear()
    val suspension = failure { coEvery { api.load(suspended(8)) } returns "eight" }
    if (suspension != "LokkiException: a receiver or an argument of coEvery suspended; evaluate it outside the block") {
        return "Fail suspension: $suspension"
    }
    if (complete { listOf(ids.next(), ids.next()) }.getOrNull() != listOf(6, 7)) {
        return "Fail: the registration bracket is not closed"
    }
    waiting.removeAt(0)()
    if (complete { api.load(8) }.isSuccess) return "Fail: the abandoned evaluation registered a stub"

    return "OK"
}
