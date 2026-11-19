// FILE: types.kt
package sample

interface Car {
    fun drive(): String
}

open class Radio {
    open fun play(): String = "jazz"
}

open class Workshop(val car: Car) {
    var radio: Radio? = null

    open fun repair(): String = car.drive()
}

class Garage(val workshop: Workshop) {
    lateinit var spare: Car
}

// FILE: FieldsTest.kt
package sample

import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy

class FieldsTest {
    @Mock
    lateinit var car: Car

    @RelaxedMock
    lateinit var relaxed: Car

    @Spy
    var radio = Radio()
}

// FILE: InjectionTest.kt
package sample

import io.lokki.InjectMocks
import io.lokki.Mock

class InjectionTest {
    @Mock
    lateinit var car: Car

    @InjectMocks
    lateinit var workshop: Workshop

    @InjectMocks
    lateinit var garage: Garage
}
