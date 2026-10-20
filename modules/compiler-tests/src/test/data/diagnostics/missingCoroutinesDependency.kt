// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.Awaits
import io.lokki.Runs
import io.lokki.andThenJust
import io.lokki.coEvery
import io.lokki.coJustAwait
import io.lokki.coJustRun
import io.lokki.just

interface Api {
    suspend fun load(): String
    suspend fun save()
}

fun test(api: Api) {
    coEvery { api.load() } <!LOKKI_MISSING_COROUTINES_DEPENDENCY!>just<!> Awaits
    coEvery { api.load() } returns "a" <!LOKKI_MISSING_COROUTINES_DEPENDENCY!>andThenJust<!> Awaits
    <!LOKKI_MISSING_COROUTINES_DEPENDENCY!>coJustAwait<!> { api.save() }

    coJustRun { api.save() }
    coEvery { api.save() } just Runs andThenJust Runs
}
