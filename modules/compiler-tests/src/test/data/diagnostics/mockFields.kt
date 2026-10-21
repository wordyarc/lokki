// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.AdditionalInterface
import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy

interface Car {
    fun drive(): String
}

open class Engine(val power: Int) {
    open fun start(): String = "started"
}

open class Part {
    open fun name(): String = "part"
}

open class Pump {
    fun prime() {}
}

open class Gearbox {
    open fun shift(): Int = 1
    fun gear(): Int = 1
}

class Bolt

class Fixture<T : Any> {
    @Mock
    lateinit var car: Car

    @Mock
    var nullableCar: Car? = null

    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@Mock<!>
    val readonlyCar: Car? = null

    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@RelaxedMock<!>
    val readonlyRelaxedCar: Car? = null

    <!LOKKI_TYPE_NOT_MOCKABLE!>@Mock<!>
    lateinit var bolt: Bolt

    <!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>@Mock<!>
    lateinit var parameter: T

    @Mock
    @AdditionalInterface(<!LOKKI_TYPE_NOT_MOCKABLE!>Part::class<!>)
    lateinit var withClass: Car

    <!LOKKI_RESTRICTED_TYPE!>@Mock<!>
    lateinit var names: List<String>

    <!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>@Mock<!>
    lateinit var gearbox: Gearbox

    @Spy
    val part = Part()

    @Spy
    var engine = Engine(1)

    @Spy
    lateinit var latePart: Part

    <!LOKKI_TYPE_NOT_MOCKABLE!>@Spy<!>
    lateinit var lateEngine: Engine

    @Spy
    var existing: Part = <!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>shared<!>

    <!LOKKI_MOCK_PROPERTY_NOT_MUTABLE!>@Spy<!>
    val readonlyExisting: Part = <!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>shared<!>

    @Spy
    var pump: Pump = <!LOKKI_UNSAFE_SPY_DELEGATION!>sharedPump<!>

    @Spy
    var constructedPump = Pump()

    @Spy
    lateinit var spiedCar: Car

    companion object {
        val shared = Part()
        val sharedPump = Pump()
    }
}

class ConstructorParameters(
    <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> val car: Car,
    <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> other: Car,
    <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@property:Mock<!> val propertyOnly: Car,
)

fun test(<!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@RelaxedMock<!> car: Car) {
}
