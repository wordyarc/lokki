// WITH_KOTLINX_COROUTINES

package sample

import io.lokki.AdditionalAnswerScope
import io.lokki.Awaits
import io.lokki.LokkiException
import io.lokki.StubScope
import io.lokki.andThenJust
import io.lokki.awaits
import io.lokki.coEvery
import io.lokki.coJustAwait
import io.lokki.coVerify
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

interface Api {
    suspend fun load(id: Int): String
    suspend fun save(id: Int)
    suspend fun count(): Int
    fun size(): Int
}

abstract class Service {
    abstract suspend fun fetch(): Long
    open suspend fun cached(): Int = 1
}

fun <T> StubScope<T>.forever(): AdditionalAnswerScope<T> = this just Awaits

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

private fun awaits(vararg calls: suspend () -> Any?): String = runBlocking {
    val jobs = calls.map { call -> launch { call() } }
    yield()
    val active = jobs.map(Job::isActive)
    jobs.forEach { it.cancelAndJoin() }
    "$active ${jobs.map(Job::isCancelled)}"
}

fun box(): String {
    val api = mock<Api>()
    val service = mock<Service> {
        coEvery { fetch() } just Awaits
    }
    coEvery { api.load(1) } just awaits
    coJustAwait { api.save(any()) }
    var second = ""
    coEvery { api.load(2) } returns "two" andThenJust Awaits
    coEvery { api.count() }.forever()
    coEvery { service.cached() } answers { 2 } andThenJust Awaits

    val states = awaits(
        { api.load(1) },
        { api.save(5) },
        {
            second = api.load(2)
            api.load(2)
        },
        { api.count() },
        { service.fetch() },
        {
            service.cached()
            service.cached()
        },
    )
    if (states != "[true, true, true, true, true, true] [true, true, true, true, true, true]") {
        return "Fail awaits: $states"
    }
    if (second != "two") return "Fail andThenJust: $second"

    coVerify {
        api.load(1)
        api.save(5)
        api.load(2)
        api.count()
        service.fetch()
    }

    every { api.size() } just Awaits
    val nonSuspend = failure { api.size() }
    if (nonSuspend != "coAnswers suspended in non-suspend Api.size(); an answer of a non-suspend member must complete without suspension") {
        return "Fail non-suspend: $nonSuspend"
    }

    return "OK"
}
