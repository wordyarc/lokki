// FILE: types.kt
package sample

interface Vehicle {
    fun park()
}

interface Car : Vehicle {
    val plate: String

    fun drive(speed: Int): String
}

interface Crate<T> {
    fun put(item: T)

    fun take(): T
}

interface Named {
    fun name(): String
}

open class Machine {
    open fun start() {}

    fun serial(): Int = 1
}

abstract class Engine : Machine() {
    abstract fun power(): Int

    open suspend fun warmUp(): Boolean = true
}

open class Radio(val station: String) {
    open fun play(): String = station
}

// FILE: InterfaceTest.kt
package sample

import io.lokki.mock

fun car(): Car = mock<Car>()

// FILE: GenericTest.kt
package sample

import io.lokki.mock

fun crate(): Crate<String> = mock<Crate<String>>()

// FILE: MoreInterfacesTest.kt
package sample

import io.lokki.mock

fun namedVehicle(): Vehicle = mock<Vehicle>(moreInterfaces = arrayOf(Named::class))

// FILE: ClassTest.kt
package sample

import io.lokki.mock

fun engine(): Engine = mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Engine<!>>()

// FILE: SpyTest.kt
package sample

import io.lokki.spy

fun radio(): Radio = spy(Radio("jazz"))

fun machine(): Machine = spy<Machine>()

fun delegate(car: Car): Car = spy(car)

// FILE: UnrelatedTest.kt
package sample

fun unrelated(car: Car): String = car.drive(1)
