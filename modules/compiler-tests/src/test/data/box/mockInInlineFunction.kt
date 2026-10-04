// MODULE: fixtures
// FILE: Fixtures.kt
package fixtures

import io.lokki.mock

interface Car {
    fun drive(): String
}

interface Boat {
    fun sail(): String
}

inline fun carFixture(): Car = mock<Car>()

inline fun <T> carFixtureIn(block: (Car) -> T): T = run { block(mock<Car>()) }

fun localCar(): Car = mock<Car>()

private inline fun privateBoatFixture(): Boat = mock<Boat>()

internal inline fun boatFixture(): Boat = privateBoatFixture()

// FILE: SamePackage.kt
package fixtures.nested

import fixtures.Boat
import fixtures.Car
import fixtures.boatFixture
import fixtures.carFixture

fun carFromAnotherPackage(): Car = carFixture()

fun boatFromAnotherPackage(): Boat = boatFixture()

// MODULE: main(fixtures)
// FILE: main.kt
package app

import fixtures.carFixture
import fixtures.carFixtureIn
import fixtures.localCar
import fixtures.nested.boatFromAnotherPackage
import fixtures.nested.carFromAnotherPackage
import io.lokki.LokkiException
import java.lang.reflect.Modifier

fun box(): String {
    val car = carFixture()
    val carClass = car.javaClass

    if (carClass.name != "fixtures.Car\$LokkiMock\$8f386350\$FixturesKt") return "Fail name: ${carClass.name}"
    if (!Modifier.isPublic(carClass.modifiers)) return "Fail: mock class created in an inline function is not public"
    if (carFixtureIn { it.javaClass } != carClass) return "Fail: mock in an inlined lambda has another class"
    if (carFromAnotherPackage().javaClass != carClass) return "Fail: mock inlined into another package"
    if (localCar().javaClass != carClass) return "Fail: mock outside of the inline function has another class"

    try {
        car.drive()
        return "Fail: no exception"
    } catch (e: LokkiException) {
        if (e.message != "no answer found for Car.drive") return "Fail message: ${e.message}"
    }

    val boatClass = boatFromAnotherPackage().javaClass
    if (!Modifier.isPublic(boatClass.modifiers)) return "Fail: mock class created in a private inline function is not public"

    return "OK"
}
