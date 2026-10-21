// FILE: chained.kt
@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.mock

fun relaxedCar(): Car = mock(relaxed = true)

fun maybeRelaxedCar(relaxed: Boolean): Car = mock(relaxed = relaxed)

fun strictCar(): Car = mock()

fun relaxedUnitCar(): Car = mock(relaxUnitFun = true)

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.clearMocks
import io.lokki.every
import io.lokki.mock

interface Handle {
    fun turn(): Int
}

interface Door {
    fun open(): Boolean
    fun close()
    fun handle(): Handle
    fun car(): Car
}

open class Wheel {
    open fun spin(): Int = 1
}

open class Locked private constructor() {
    open fun open(): Boolean = false
}

interface Parser<T> {
    fun parse(): T
    fun many(): Parser<List<T>>
}

interface Car {
    fun door(index: Int): Door
    fun wheel(): Wheel
    fun locked(): Locked
    fun parser(): Parser<String>
    fun items(): List<Door>
    fun <R> convert(): R
}

private fun message(block: () -> Any?): String =
    try {
        "no exception: ${block()}"
    } catch (e: LokkiException) {
        e.message!!
    }

fun box(): String {
    val car = relaxedCar()
    val door = car.door(1)
    if (door !== car.door(1) || door === car.door(2)) return "Fail child cache"
    if (!"$door".startsWith("Door(child of #")) return "Fail child name: $door"
    if (door.open() || door.handle().turn() != 0) return "Fail relaxed grandchild"
    door.close()
    if (door.car().door(3).handle() !== door.car().door(3).handle()) return "Fail cycle"
    if (car.wheel().spin() != 0) return "Fail class child"
    if (car.items() != emptyList<Door>()) return "Fail default before child"
    if (car.parser().parse() != "") return "Fail generic child"

    val locked = message { car.locked() }
    if (locked != "relaxed mock has no default for Locked returned by $car.locked(); stub the call") {
        return "Fail unmockable: $locked"
    }
    val parser = car.parser()
    val many = message { parser.many() }
    if (many != "relaxed mock has no default for Parser<List<String>> returned by $parser.many(); stub the call") {
        return "Fail expanding type: $many"
    }
    val converted = message { car.convert<Door>() }
    val typeParameter = "relaxed mock has no default for type parameter R"
    if (!converted.startsWith(typeParameter)) return "Fail type parameter: $converted"

    every { car.door(1).open() } returns true
    if (!car.door(1).open() || car.door(1) !== door) return "Fail stub on relaxed child"

    if (maybeRelaxedCar(relaxed = true).door(1).handle().turn() != 0) return "Fail relaxed flag in variable"
    val strict = message { maybeRelaxedCar(relaxed = false).door(1) }
    if (!strict.startsWith("no answer found for")) return "Fail strict flag in variable: $strict"
    if (!message { strictCar().door(1) }.startsWith("no answer found for")) return "Fail strict mock"
    if (!message { relaxedUnitCar().door(1) }.startsWith("no answer found for")) return "Fail relaxUnitFun"

    clearMocks(car, answers = false, recordedCalls = false, childMocks = false)
    if (car.door(2) !== car.door(2)) return "Fail kept children"
    val second = car.door(2)
    clearMocks(car)
    if (car.door(2) === second) return "Fail cleared children"

    val plain = mock<Car>(relaxed = true)
    val hinted = message { plain.door(1) }
    val hint = "relaxed mock has no default for Door returned by $plain.door(1); stub the call or annotate the file " +
        "that creates the mock with @file:ChainedMocks"
    if (hinted != hint) return "Fail hint: $hinted"
    val final = message { plain.locked() }
    if (final != "relaxed mock has no default for Locked returned by $plain.locked(); stub the call") {
        return "Fail hint for unmockable type: $final"
    }
    return "OK"
}
