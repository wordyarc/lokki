package io.lokki.it

import io.lokki.coEvery
import io.lokki.coJustRun
import io.lokki.mock
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class StubSuspendMemberTest {
    private val waiting = ArrayList<Continuation<Unit>>()

    @Test
    fun suspendingAnswerReachesMain() {
        val navigator = mock<Navigator>()
        coJustRun { navigator.reset() }
        coEvery { navigator.route(any()) } coAnswers {
            suspendCoroutine { waiting += it }
            listOf("home", firstArg())
        }

        assertEquals(Result.success("home -> work"), complete { Trip(navigator).plan("work") })
    }

    private fun <T> complete(block: suspend () -> T): Result<T> {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        while (outcome == null) waiting.removeAt(0).resume(Unit)
        return outcome!!
    }
}
