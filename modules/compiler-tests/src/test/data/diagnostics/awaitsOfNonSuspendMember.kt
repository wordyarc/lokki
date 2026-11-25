// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// WITH_KOTLINX_COROUTINES

package sample

import io.lokki.AdditionalAnswerScope
import io.lokki.Awaits
import io.lokki.StubScope
import io.lokki.andThenJust
import io.lokki.coEvery
import io.lokki.coJustAwait
import io.lokki.every
import io.lokki.just

interface Api {
    suspend fun load(id: Int): String
    suspend fun save(id: Int)
    fun size(): Int
    fun reset()
    val name: String
    var limit: Int
}

fun <T> StubScope<T, T>.forever(): AdditionalAnswerScope<T, T> = this just Awaits

fun test(api: Api, fetch: suspend (Int) -> String, count: (Int) -> Int) {
    every { api.size() } <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>just<!> Awaits
    coEvery { api.size() } <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>just<!> Awaits
    every { api.size() } returns 1 <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>andThenJust<!> Awaits
    <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>coJustAwait<!> { api.reset() }
    every { api.name } <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>just<!> Awaits
    every { api.limit = any() } <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>just<!> Awaits
    every { count(any()) } <!LOKKI_AWAITS_OF_NON_SUSPEND_MEMBER!>just<!> Awaits
    every { api.size() }.forever()

    coEvery { api.load(1) } just Awaits
    coEvery { api.load(2) } returns "two" andThenJust Awaits
    coJustAwait { api.save(3) }
    coEvery { fetch(any()) } just Awaits
}
