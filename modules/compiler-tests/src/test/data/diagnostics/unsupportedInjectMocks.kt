// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress(
    "LOKKI_UNSUPPORTED_INJECTION",
    "LOKKI_MOCK_PROPERTY_NOT_MUTABLE",
    "LOKKI_UNSUPPORTED_MOCK_PARAMETER",
)

package sample

import io.lokki.InjectMocks
import io.lokki.Mock

interface Repo

class Service(val repo: Repo)

class Fixture(<!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks service: Service<!>) {
    @Mock
    lateinit var repo: Repo

    <!LOKKI_UNSUPPORTED!>@Mock
    @InjectMocks
    lateinit var both: Service<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    val readonly: Service? = null<!>
}
