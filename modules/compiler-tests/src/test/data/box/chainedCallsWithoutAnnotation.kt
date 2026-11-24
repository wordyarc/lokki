package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Handle {
    fun turn(): Int
}

interface Door {
    fun open(): Boolean
    fun handle(): Handle
}

interface Car {
    fun door(index: Int): Door
    fun speed(): Int
    fun drive(speed: Int): Boolean
}

class Fixture(val car: Car)

private fun message(block: () -> Unit): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!
    }

fun box(): String {
    val car = mock<Car>()
    every { car.door(1).open() } returns true
    every { car.door(more(1)).handle().turn() } returns 3
    if (!car.door(1).open() || car.door(2).handle().turn() != 3) return "Fail chain"
    verify { car.door(1).open() }

    every { Fixture(car).car.door(4).open() } returns true
    if (!car.door(4).open()) return "Fail link behind a holder"

    val other = mock<Car>()
    val argument = message { every { car.drive(other.speed()) } returns true }
    if (!argument.startsWith("no answer found for $other.speed()") || "call chain" in argument) {
        return "Fail argument: $argument"
    }
    val outside = message { car.speed() }
    if (!outside.startsWith("no answer found for $car.speed()") || "call chain" in outside) {
        return "Fail outside: $outside"
    }

    val relaxed = mock<Car>(relaxed = true)
    every { relaxed.door(5).open() } returns true
    every { relaxed.door(6).handle().turn() } returns 6
    val child = relaxed.door(5)
    if (!child.open() || relaxed.door(6).handle().turn() != 6) return "Fail relaxed parent"
    val hint = "; stub the call or annotate the declaration in chainedCallsWithoutAnnotation.kt that creates " +
        "the mock with @ChainedMocks"
    val grandchild = message { child.handle() }
    if (!grandchild.endsWith("returned by $child.handle()$hint")) return "Fail relaxed child: $grandchild"
    val noDefault = message { relaxed.door(7) }
    if (!noDefault.endsWith("returned by $relaxed.door(7)$hint")) return "Fail relaxed hint: $noDefault"
    return "OK"
}
