package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

interface Car {
    fun drive(): String
}

fun box(): String {
    val car = mock<Car>()
    val other = mock<Car>()
    val name = car.toString()

    if (car != car || car == other || car.equals(null)) return "Fail: equals is not identity"
    if (car.hashCode() != System.identityHashCode(car)) return "Fail: hashCode is not identity"
    if (setOf(car, other, car).size != 2) return "Fail: mocks in a set"

    every { car.toString() } returns "my car"
    if ("$car" != "my car" || other.toString() == "my car") return "Fail: toString"

    val any: Any = car
    every { any.hashCode() } returns 7
    if (car.hashCode() != 7) return "Fail: hashCode stubbed through Any"

    every { car.equals(other) } returns true
    if (car != other || other == car || car == mock<Car>()) return "Fail: equals of a value"

    every { car.equals(car) } returns false
    if (car == car) return "Fail: equals of the mock itself"

    every { car.equals(any()) } returns true
    if (car != Any()) return "Fail: equals of any value"

    try {
        car.drive()
        return "Fail: no exception"
    } catch (e: LokkiException) {
        if (!e.message!!.startsWith("no answer found for $name.drive() ")) return "Fail message: ${e.message}"
        if ("$name.hashCode()\n$name.toString()" !in e.message!!) return "Fail configured answers: ${e.message}"
    }

    return "OK"
}
