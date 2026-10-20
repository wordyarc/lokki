package sample

import io.lokki.coEvery
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Api {
    suspend fun fetch(id: Int, limit: Int = 10, fresh: Boolean = false): String
    suspend fun page(from: Int, to: Int = from + 10): String
    suspend fun load(token: String = currentToken()): String
    fun defaultLimit(): Int
    suspend fun list(limit: Int = defaultLimit()): String
    suspend fun count(step: Int = counter++): String
    suspend fun String.format(width: Int = 2): String
    fun size(id: Int, scale: Int = 1): Int
}

open class Store {
    open suspend fun get(key: String, fresh: Boolean = false): String = "original"
}

private var counter = 0

fun currentToken(): String = "token"

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: Exception) {
        "${e::class.simpleName}: ${e.message}".replace(Regex("""\(#\d+\)"""), "")
    }

private fun Result<*>.problem(): String? =
    exceptionOrNull()?.message?.lineSequence()?.first()?.replace(Regex("""\(#\d+\)"""), "")

suspend fun stubInside(api: Api) {
    coEvery { api.fetch(3) } returns "three"
}

fun box(): String {
    val api = mock<Api>()

    coEvery { api.fetch(1) } returns "one"
    if (complete { api.fetch(1) + api.fetch(1, 10) + api.fetch(1, 10, false) }.getOrNull() != "oneoneone") {
        return "Fail omitted defaults"
    }
    val strict = complete { api.fetch(1, 11) }.problem()
    if (strict != "no answer found for Api.fetch(1, 11, false) among the configured answers: " +
        "(Api.fetch(eq(1), eq(10), eq(false)))"
    ) {
        return "Fail: the default is not a value: $strict"
    }

    coEvery { api.fetch(any(), fresh = true) } returns "fresh"
    if (complete { api.fetch(2, 10, true) }.getOrNull() != "fresh") return "Fail matcher with defaults"

    coEvery { api.page(5) } returns "page"
    if (complete { api.page(5, 15) }.getOrNull() != "page") return "Fail default of a value argument"

    coEvery { api.load() } returns "loaded"
    if (complete { api.load("token") }.getOrNull() != "loaded") return "Fail default with a call"

    coEvery { api.defaultLimit() } returnsMany listOf(3, 4)
    coEvery { api.list() } returns "listed"
    if (complete { api.list(3) }.getOrNull() != "listed") return "Fail default of a mock member"
    if (listOf(api.defaultLimit(), api.defaultLimit()) != listOf(3, 4)) return "Fail: registration moved the answers"

    val unstable = failure { coEvery { api.count() } returns "counted" }
    if (unstable != "LokkiException: default value of 'step' is unstable, has no equals, " +
        "or depends on a matcher argument; pass it explicitly or use any()"
    ) {
        return "Fail unstable default: $unstable"
    }

    with(api) {
        coEvery { any<String>().format() } returns "formatted"
        if (complete { "a".format(2) }.getOrNull() != "formatted") return "Fail matcher of an extension receiver"
    }

    coEvery { api.size(1) } returns 1
    if (api.size(1, 1) != 1) return "Fail non-suspend member in coEvery"

    if (complete { stubInside(api); api.fetch(3, 10) }.getOrNull() != "three") return "Fail coEvery in suspend function"

    val store = mock<Store>()
    coEvery { store.get("key") } returns "stubbed"
    if (complete { store.get("key", false) }.getOrNull() != "stubbed") return "Fail mock of a class"

    return "OK"
}
