package sample

import io.lokki.Answer
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.coJustRun
import io.lokki.every
import io.lokki.just
import io.lokki.justRun
import io.lokki.mock
import io.lokki.slot
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

@JvmInline
value class Token(val value: Int)

interface Api {
    suspend fun load(id: Int): String
    suspend fun count(): Int
    suspend fun total(): Long
    suspend fun find(name: String): String?
    suspend fun <T> echo(value: T): T
    suspend fun token(): Token
    suspend fun maybeToken(): Token?
    suspend fun result(): Result<String>
    suspend fun save(id: Int, name: String)
    fun size(): Int
    fun reset()
}

abstract class Service {
    abstract suspend fun fetch(id: Int): String
    open suspend fun cached(): Int = 1
}

private val waiting = ArrayList<() -> Unit>()
private var pauses = 0

suspend fun pause(): Unit = suspendCoroutine { continuation ->
    pauses++
    waiting += { continuation.resume(Unit) }
}

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    while (outcome == null) waiting.removeAt(0)()
    return outcome!!
}

private fun Result<*>.failure(): String? = exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" }

private fun Result<*>.problem(): String? = exceptionOrNull()?.message?.replace(Regex("""\(#\d+\)"""), "")

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: Exception) {
        "${e::class.simpleName}: ${e.message}"
    }

suspend fun stubInside(api: Api) {
    coEvery { api.load(2) } returns "two"
}

fun box(): String {
    val api = mock<Api>()

    val strict = complete { api.load(1) }.problem()
    if (strict != "no answer found for Api.load(1) among the configured answers: ()\nCalled member: Api.load(Int): String") {
        return "Fail strict: $strict"
    }

    coEvery { api.load(1) } returns "one"
    coEvery { api.load(more(5)) } returns "many"
    if (complete { api.load(1) + api.load(9) }.getOrNull() != "onemany") return "Fail returns"

    coEvery { api.count() } coAnswers {
        pause()
        42
    }
    coEvery { api.total() } coAnswers {
        pause()
        7L
    }
    coEvery { api.find("none") } coAnswers {
        pause()
        null
    }
    coEvery { api.echo<String>(any()) } coAnswers {
        pause()
        firstArg<String>() + "!"
    }
    coEvery { api.token() } coAnswers {
        pause()
        Token(3)
    }
    coEvery { api.maybeToken() } coAnswers {
        pause()
        Token(4)
    }
    coEvery { api.result() } coAnswers {
        pause()
        Result.success("r")
    }
    val saved = slot<String>()
    coEvery { api.save(1, capture(saved)) } coAnswers { pause() }
    pauses = 0
    val suspended = complete {
        listOf(
            api.count(), api.total(), api.find("none"), api.echo("hi"), api.token(), api.maybeToken(), api.result(),
            api.save(1, "name"),
        )
    }.getOrNull()
    val expected = listOf(42, 7L, null, "hi!", Token(3), Token(4), Result.success("r"), Unit)
    if (suspended != expected) return "Fail suspended answers: $suspended"
    if (pauses != expected.size) return "Fail: answers suspended $pauses times"
    if (saved.captured != "name") return "Fail capture: ${saved.captured}"

    val mockClass = api.javaClass
    val continuations = listOf("load", "count", "total", "find", "echo", "token", "maybeToken", "save", "result")
        .filter { runCatching { Class.forName("${mockClass.name}\$$it\$1", false, mockClass.classLoader) }.isSuccess }
    if (continuations != listOf("result")) return "Fail continuation classes: $continuations"

    coEvery { api.echo<Int>(any()) } answers { firstArg<Int>() + 1 }
    coEvery { api.load(3) } answers(Answer { "answer" })
    coEvery { api.load(4) } throws IllegalStateException("four")
    coEvery { api.load(5) } returns "first" andThen "second"
    if (complete { api.echo(1) }.getOrNull() != 2) return "Fail answers"
    if (complete { api.load(3) }.getOrNull() != "answer") return "Fail Answer"
    if (complete { api.load(4) }.failure() != "IllegalStateException: four") return "Fail throws"
    if (complete { listOf(api.load(5), api.load(5), api.load(5)) }.getOrNull() != listOf("first", "second", "second")) {
        return "Fail andThen"
    }

    coEvery { api.find(any()) } returnsArgument 0
    if (complete { api.find("same") }.getOrNull() != "same") return "Fail returnsArgument"

    coEvery { api.load(6) } coAnswers {
        pause()
        if (self !== api || args != listOf<Any?>(6)) "another call" else "${method.name}:${firstArg<Int>()}"
    }
    if (complete { api.load(6) }.getOrNull() != "load:6") return "Fail answer scope"

    if (complete { stubInside(api); api.load(2) }.getOrNull() != "two") return "Fail coEvery in a suspend function"

    coJustRun { api.save(2, any()) }
    if (complete { api.save(2, "a") }.isFailure) return "Fail coJustRun"
    coEvery { api.save(3, any()) } just Runs
    if (complete { api.save(3, "a") }.isFailure) return "Fail just Runs"

    coEvery { api.size() } returns 3
    if (api.size() != 3) return "Fail coEvery of a non-suspend member"
    every { api.size() } coAnswers { 4 }
    if (api.size() != 4) return "Fail coAnswers of a non-suspend member"
    every { api.size() } coAnswers {
        pause()
        5
    }
    val nonSuspend = failure { api.size() }.replace(Regex("""\(#\d+\)"""), "")
    if (nonSuspend != "LokkiException: coAnswers suspended in non-suspend Api.size(); " +
        "an answer of a non-suspend member must complete without suspension"
    ) {
        return "Fail suspension of a non-suspend member: $nonSuspend"
    }
    waiting.clear()

    justRun { api.reset() }
    api.reset()
    justRun { api.reset() } andThenThrows IllegalStateException("reset")
    if (failure { api.reset(); api.reset() } != "IllegalStateException: reset") return "Fail justRun andThenThrows"

    val service = mock<Service>()
    coEvery { service.fetch(any()) } coAnswers {
        pause()
        "fetched ${firstArg<Int>()}"
    }
    coEvery { service.cached() } returns 2
    if (complete { service.fetch(1) + service.cached() }.getOrNull() != "fetched 12") return "Fail mock of a class"

    val function = mock<suspend (Int) -> String>()
    coEvery { function(1) } returns "one"
    coEvery { function.invoke(more(1)) } coAnswers {
        pause()
        "many"
    }
    if (complete { function(1) + function(2) }.getOrNull() != "onemany") return "Fail suspend function type"
    val suspending: suspend () -> Int = mock()
    coEvery { suspending() } returns 1
    if (complete { suspending() }.getOrNull() != 1) return "Fail suspend function type without parameters"

    val relaxed = mock<Api>(relaxed = true)
    val defaults = complete { listOf(relaxed.load(1), relaxed.count(), relaxed.find("x"), relaxed.save(1, "a")) }
    if (defaults.getOrNull() != listOf("", 0, null, Unit)) return "Fail relaxed: $defaults"
    val token = complete { relaxed.token() }.problem()
    if (token != "relaxed mock has no default for Token returned by Api.token(); stub the call") {
        return "Fail relaxed value class: $token"
    }
    val unitFun = mock<Api>(relaxUnitFun = true)
    if (complete { unitFun.save(1, "a") }.isFailure) return "Fail relaxUnitFun"
    if (complete { unitFun.count() }.isSuccess) return "Fail: relaxUnitFun answers a non-Unit member"

    return "OK"
}
