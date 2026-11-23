// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress("LOKKI_MOCK_PROPERTY_NOT_MUTABLE", "LOKKI_UNSUPPORTED_MOCK_PARAMETER", "LOKKI_TYPE_NOT_MOCKABLE")

package sample

import io.lokki.Mock

interface Car

class Bolt

<!LOKKI_UNSUPPORTED!>@Mock
lateinit var topLevel: Car<!>

class Fixture(@Mock car: Car) {
    <!LOKKI_UNSUPPORTED!>@Mock
    val readonly: Car? = null<!>

    <!LOKKI_UNSUPPORTED!>@Mock
    var computed: Car
        get() = TODO()
        set(value) {}<!>

    <!LOKKI_UNSUPPORTED!>@Mock
    lateinit var bolt: Bolt<!>
}
