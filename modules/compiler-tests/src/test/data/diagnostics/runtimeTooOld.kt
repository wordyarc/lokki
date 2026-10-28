// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// FILE: LokkiAbi.kt
package io.lokki.internal

internal object LokkiAbi {
    const val VERSION: Int = 0
}

// FILE: test.kt
package test

import io.lokki.Mock
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import io.lokki.verify

interface Car {
    fun drive(speed: Int): String
}

class CarTest {
    <!LOKKI_RUNTIME_TOO_OLD!>@Mock<!>
    lateinit var car: Car

    fun test() {
        val other = <!LOKKI_RUNTIME_TOO_OLD!>mock<Car>()<!>
        <!LOKKI_RUNTIME_TOO_OLD!>every { other.drive(any()) }<!> returns "a"
        <!LOKKI_RUNTIME_TOO_OLD!>excludeRecords { other.drive(1) }<!>
        <!LOKKI_RUNTIME_TOO_OLD!>verify { other.drive(2) }<!>
    }
}
