// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress(
    "LOKKI_UNRESOLVED_INJECTION",
    "LOKKI_AMBIGUOUS_INJECTION",
    "LOKKI_UNSUPPORTED_INJECTION",
    "LOKKI_MOCK_PROPERTY_NOT_MUTABLE",
    "LOKKI_UNSUPPORTED_MOCK_PARAMETER",
)

package sample

import io.lokki.InjectMocks
import io.lokki.Mock

interface Repo

interface Listener

class Service(val repo: Repo)

class Listened(val listener: Listener)

class Guarded {
    private var repo: Repo? = null
}

class First(val second: Second)

class Second(val first: First)

class Fixture(<!LOKKI_UNSUPPORTED!>@InjectMocks service: Service<!>) {
    @Mock
    lateinit var repo: Repo

    @Mock
    lateinit var first: Listener

    @Mock
    lateinit var second: Listener

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    lateinit var listened: Listened<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    lateinit var guarded: Guarded<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    lateinit var interfaceTarget: Listener<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    lateinit var cycleFirst: First<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    lateinit var cycleSecond: Second<!>

    <!LOKKI_UNSUPPORTED!>@Mock
    @InjectMocks
    lateinit var both: Service<!>

    <!LOKKI_UNSUPPORTED!>@InjectMocks
    val readonly: Service? = null<!>
}
