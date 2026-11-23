// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package test

import io.lokki.InjectMocks
import io.lokki.Mock

interface Car

class Service(val car: Car)

class First(val second: Second)

class Second(val first: First)

class Tests {
    fun ambiguous(@Mock left: Car, @Mock right: Car, <!LOKKI_AMBIGUOUS_INJECTION!>@InjectMocks service: Service<!>) = Unit

    fun missing(<!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks service: Service<!>) = Unit

    fun cycle(<!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks first: First<!>, <!LOKKI_UNRESOLVED_INJECTION!>@InjectMocks second: Second<!>) = Unit
}
