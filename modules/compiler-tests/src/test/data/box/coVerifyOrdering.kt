package sample

import io.lokki.Ordering
import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.coVerifyAll
import io.lokki.coVerifyOrder
import io.lokki.coVerifySequence
import io.lokki.confirmVerified
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Session {
    suspend fun load(id: Int): String
}

interface Api {
    suspend fun load(id: Int, fresh: Boolean = false): String
    suspend fun session(id: Int): Session
}

private val log = ArrayList<String>()

suspend fun <T> logged(label: String, value: T): T {
    log += label
    return value
}

fun <T> complete(block: suspend () -> T): Result<T> {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!
}

private fun message(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }

fun box(): String {
    val api = mock<Api>()
    val name = "$api"
    val session = mock<Session>()
    coEvery { api.load(any(), any()) } returns "loaded"
    coEvery { api.session(any()) } returns session
    coEvery { session.load(any()) } returns "session"
    complete {
        api.load(1)
        api.session(2).load(3)
        api.load(4, fresh = true)
    }

    coVerifyOrder {
        api.load(1)
        api.load(logged("id", 4), true)
    }
    coVerifySequence {
        api.load(1)
        logged("receiver", api).session(2).load(3)
        api.load(4, true)
    }
    coVerifyAll {
        api.load(any(), any())
        api.session(2).load(any())
    }
    coVerify(Ordering.SEQUENCE, inverse = true) { api.load(1) }
    coVerifyOrder(inverse = true) {
        api.load(4, true)
        api.load(1)
    }
    if (log != listOf("id", "receiver")) return "Fail order of evaluation: $log"

    val sequence = message { coVerifySequence { api.load(1) } }
    val expected = "Verification failed: number of calls happened not matching exact number of verification sequence\n\n" +
        "Matchers: \n+$name.load(eq(1), eq(false))\n\nCalls:\n1) +$name.load(1, false)\n2) $name.session(2)\n" +
        "3) $name.load(4, true)\n"
    if (sequence?.startsWith(expected) != true) return "Fail sequence: $sequence"

    confirmVerified(api, session)

    return "OK"
}
