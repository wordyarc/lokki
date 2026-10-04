// FILE: Car.kt
package sample

import io.lokki.mock

interface Vehicle {
    val wheels: Int
    fun park()
}

interface Car : Vehicle, Comparable<Car> {
    var driver: String?
    fun drive(distance: Int, fast: Boolean): String
    fun speed(): Double
    fun <T> transport(cargo: T): List<T>
    fun String.label(): String
    fun route(vararg stops: String): Int
}

interface Engine {
    fun start(): Boolean
}

interface Radio {
    fun start(): Boolean
}

interface Dashboard : Engine, Radio

fun newCar(): Car = mock<Car>()

fun newDashboard(): Dashboard = mock<Dashboard>()

// FILE: box.kt
package sample

import io.lokki.LokkiException
import io.lokki.every

private fun failure(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: LokkiException) {
        e.message?.replace(CALL, ".$1")
    }

private val CALL = Regex("""\(#\d+\)\.(\w+)\(.*""", RegexOption.DOT_MATCHES_ALL)

fun box(): String {
    val car = newCar()

    every { car.drive(10, any()) } returns "ten"
    every { car.drive(any(), true) } returns "fast"
    if (car.drive(10, false) != "ten") return "Fail: value and matcher"
    if (car.drive(10, true) != "fast" || car.drive(5, true) != "fast") return "Fail: the last matching stub answers"
    if (failure { car.drive(5, false) } != "no answer found for Car.drive") return "Fail: no matching stub"

    every { car.wheels } returns 4
    if (car.wheels != 4) return "Fail: getter of a supertype property"

    every { car.speed() } returns 99.5
    if (car.speed() != 99.5) return "Fail: primitive answer"

    every { car.driver } returns null
    if (car.driver != null) return "Fail: null answer"

    every { car.driver = "me" } returns Unit
    car.driver = "me"
    if (failure { car.driver = "you" } != "no answer found for Car.setDriver") return "Fail: setter"

    every { car.park() } returns Unit
    car.park()

    every { car.transport("box") } returns listOf("box")
    if (car.transport("box") != listOf("box")) return "Fail: generic member"

    with(car) {
        every { "x".label() } returns "labelled"
        if ("x".label() != "labelled") return "Fail: extension receiver"
        if (failure { "y".label() } != "no answer found for Car.label") return "Fail: another extension receiver"
    }

    every { car.compareTo(car) } returns 0
    if (car.compareTo(car) != 0) return "Fail: member of a generic supertype"
    if (failure { car.compareTo(newCar()) } != "no answer found for Car.compareTo") return "Fail: another mock"

    every { car.route("a", "b") } returns 2
    if (car.route("a", "b") != 2) return "Fail: vararg values"
    if (failure { car.route("b", "a") } != "no answer found for Car.route") return "Fail: other vararg values"

    every { car.drive(lazy { 42 }.value, false) } returns "computed"
    if (car.drive(42, false) != "computed") return "Fail: argument with a lambda"

    if (Garage().car.speed() != 50.0) return "Fail: stub in a property initializer"

    every { car.driver = any() } returns Unit
    car.driver = "anyone"

    val vehicle: Vehicle = car
    every { vehicle.wheels } returns 6
    if (car.wheels != 6) return "Fail: stub through a supertype"

    val radio: Radio = newDashboard()
    every { radio.start() } returns true
    if (!(radio as Engine).start()) return "Fail: member with two root declarations"

    val text: CharSequence = "abc"
    if (failure { every { text.length } returns 3 } != "abc is not a lokki mock") return "Fail: stub of a non-mock"

    return "OK"
}

class Garage {
    val car: Car = newCar()
    private val stubbed = every { car.speed() } returns 50.0
}
