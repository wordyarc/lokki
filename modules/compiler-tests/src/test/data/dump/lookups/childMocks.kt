// FILE: types.kt
package sample

interface Door {
    fun open(): Boolean
}

interface Seat {
    fun recline()
}

interface Car {
    fun door(index: Int): Door

    fun seat(): Seat

    fun plate(): String
}

// FILE: ChainTest.kt
package sample

import io.lokki.every
import io.lokki.mock

fun chained(): Car {
    val car = mock<Car>()
    every { car.door(1).open() } returns true
    return car
}

// FILE: RelaxedTest.kt
@file:RelaxedChildMocks

package sample

import io.lokki.RelaxedChildMocks
import io.lokki.mock

fun relaxed(): Car = mock<Car>(relaxed = true)

// FILE: PlainTest.kt
package sample

import io.lokki.mock

fun plain(): Car = mock<Car>(relaxed = true)
