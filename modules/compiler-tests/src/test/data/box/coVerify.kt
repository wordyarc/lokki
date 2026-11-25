package sample

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.checkEquals
import io.lokki.coEvery
import io.lokki.coExcludeRecords
import io.lokki.coVerify
import io.lokki.coVerifyCount
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface Session {
    suspend fun load(id: Int): String
}

interface Api {
    suspend fun load(id: Int, fresh: Boolean = false): String
    suspend fun save(name: String)
    suspend fun session(id: Int, fresh: Boolean = false): Session
    suspend fun next(): Int
    fun size(): Int
}

private val log = ArrayList<String>()
private val waiting = ArrayList<() -> Unit>()

suspend fun <T> logged(label: String, value: T): T {
    log += label
    return value
}

suspend fun suspended(value: Int): Int = suspendCoroutine { continuation ->
    waiting += { continuation.resume(value) }
}

fun MatcherScope.positive(): Int = more(0)

suspend fun MatcherScope.atLeast(value: Int): Int = more(logged("helper", value), andEquals = true)

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    while (outcome == null) waiting.removeAt(0)()
    return outcome!!
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun message(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    } catch (e: LokkiException) {
        e.message
    }

suspend fun verifyInside(api: Api) {
    coVerify { api.load(2, logged("inside", true)) }
}

fun box(): String {
    val api = mock<Api>()
    val name = "$api"
    val session = mock<Session>()
    coEvery { api.load(any(), any()) } returns "loaded"
    coEvery { api.save(any()) } returns Unit
    coEvery { api.session(any(), any()) } returns session
    coEvery { session.load(any()) } returns "session"
    every { api.size() } returns 0

    coVerify(exactly = 0) { api.load(any(), any()) }
    complete {
        api.load(1)
        api.load(2, fresh = true)
        api.save("a")
    }
    coVerify { api.load(1) }
    coVerify { api.load(2, true) }
    coVerify(exactly = 2) { api.load(any(), any()) }
    coVerify(atLeast = 1, atMost = 1) { api.save(any()) }
    coVerify(inverse = true) { api.load(3) }
    coVerify {
        api.load(1)
        api.save("a")
    }
    val missing = message { coVerify { api.load(3) } }
    if (missing != "Verification failed: call 1 of 1: $name.load(eq(3), eq(false)). No matching calls found.\n\n" +
        "Calls to same method:\n" +
        "1) $name.load(1, false)\n" +
        "   [0]: argument: 1, matcher: eq(3), result: -\n" +
        "   [1]: argument: false, matcher: eq(false), result: +\n" +
        "2) $name.load(2, true)\n" +
        "   [0]: argument: 2, matcher: eq(3), result: -\n" +
        "   [1]: argument: true, matcher: eq(false), result: -\n\n$CALL_SITES"
    ) {
        return "Fail failure text: $missing"
    }

    log.clear()
    coVerify { logged("receiver", api).load(logged("id", 1), logged("fresh", false)) }
    coVerify {
        api.load(positive())
        api.load(atLeast(2), true)
        api.load(positive(), false)
    }
    if (log != listOf("receiver", "id", "fresh", "helper")) return "Fail order: $log"

    val ids = mock<Api>()
    coEvery { ids.next() } returnsMany listOf(1, 2)
    coVerify { api.load(ids.next()) }
    coVerify(exactly = 0) { ids.next() }
    if (complete { ids.next() }.getOrNull() != 1) return "Fail: verification moved the answers"

    val saved = slot<String>()
    coVerify { api.save(capture(saved)) }
    if (saved.captured != "a") return "Fail capture: ${saved.captured}"
    coVerify { api.save(withArg { checkEquals("a") }) }

    val unused = mock<Api>()
    coVerify { logged("unused", unused) wasNot Called }
    if (message { coVerify { logged("used", ids) wasNot Called } }?.startsWith("Verification failed: $ids") != true) {
        return "Fail wasNot Called of a used mock"
    }

    api.size()
    coVerify { api.size() }

    if (complete { api.session(7).load(8) }.getOrNull() != "session") return "Fail chain"
    coVerify { api.session(7).load(8) }
    val link = message { coVerify { api.session(9).load(8) } }
    if (link?.startsWith("Verification failed: call 1 of 2: $name.session(eq(9), eq(false)).") != true) {
        return "Fail link: $link"
    }
    val fresh = message { coVerify { api.session(7, fresh = true).load(8) } }
    if (fresh?.startsWith("Verification failed: call 1 of 2: $name.session(eq(7), eq(true)).") != true) {
        return "Fail link with a default: $fresh"
    }

    val suspension = message { coVerify { api.load(suspended(1)) } }
    if (suspension != "a receiver or an argument of coVerify suspended; evaluate it outside the block") {
        return "Fail suspension: $suspension"
    }
    waiting.clear()

    if (complete { verifyInside(api) }.isFailure) return "Fail coVerify in a suspend function"

    coVerifyCount {
        2 * { api.load(any(), any()) }
        (1..2) * { api.save(any()) }
    }
    val count = message { coVerifyCount { 3 * { api.load(any(), any()) } } }
    if (count?.startsWith("Verification failed: call 1 of 1: $name.load(any(), any()). 2 matching calls found") != true) {
        return "Fail coVerifyCount: $count"
    }

    coExcludeRecords { api.load(1) }
    coVerify(exactly = 0) { api.load(1) }
    coExcludeRecords { logged("excluded", api).save(any()) }
    coVerify(exactly = 0) { api.save(any()) }
    confirmVerified(api, session)

    return "OK"
}
