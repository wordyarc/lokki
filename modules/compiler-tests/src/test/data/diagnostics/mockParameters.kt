// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package test

import io.lokki.InjectMocks
import io.lokki.Mock

interface Car

class FinalCar

class Service(val car: Car)

class Valid(@Mock val car: Car) {
    fun method(@Mock car: Car, @InjectMocks service: Service) = Unit
}

class Rejected {
    fun final(<!LOKKI_TYPE_NOT_MOCKABLE!>@Mock<!> car: FinalCar) = Unit

    fun both(@Mock <!LOKKI_UNSUPPORTED_INJECTION!>@InjectMocks<!> service: Service) = Unit

    var property: Car?
        get() = null
        set(<!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> value) = Unit
}

fun topLevel(<!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> car: Car) = Unit

fun local() {
    class Local {
        fun method(<!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> car: Car) = Unit
    }
}
