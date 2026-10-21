// FILE: chained.kt
@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.every

fun stubInChainedFile(car: Car) {
    every { car.door(1).open() } returns true
}

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import io.lokki.verify

interface Door {
    fun open(): Boolean
}

interface Car {
    fun door(index: Int): Door
    suspend fun garage(): Car
    fun speed(): Int
    fun drive(speed: Int): Boolean
}

class Fixture(val car: Car)

private const val HINT = "\nThe call is a link of a call chain in a stubbed or verified call: " +
    "stub it first or annotate the file with @file:ChainedMocks"

private fun message(block: () -> Unit): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!
    }

private fun hinted(message: String, start: String): Boolean = message.startsWith(start) && message.endsWith(HINT)

fun box(): String {
    val car = mock<Car>()
    val name = "$car"
    val unanswered = "no answer found for $name.door(1) among the configured answers: ()"

    val stub = message { every { car.door(1).open() } returns true }
    if (!hinted(stub, unanswered)) return "Fail every: $stub"
    val verified = message { verify { car.door(1).open() } }
    if (!hinted(verified, unanswered)) return "Fail verify: $verified"
    val excluded = message { excludeRecords { car.door(1).open() } }
    if (!hinted(excluded, unanswered)) return "Fail excludeRecords: $excluded"
    val fixture = message { every { Fixture(car).car.door(1).open() } returns true }
    if (!hinted(fixture, unanswered)) return "Fail fixture: $fixture"

    val garage = "no answer found for $name.garage() among the configured answers: ()"
    val suspended = message { coEvery { car.garage().speed() } returns 1 }
    if (!hinted(suspended, garage)) return "Fail coEvery: $suspended"
    val coVerified = message { coVerify { car.garage().speed() } }
    if (!hinted(coVerified, garage)) return "Fail coVerify: $coVerified"

    val relaxed = mock<Car>(relaxed = true)
    val noDefault = message { every { relaxed.door(1).open() } returns true }
    if (!noDefault.endsWith("returned by $relaxed.door(1); stub the call$HINT")) return "Fail relaxed: $noDefault"

    val other = mock<Car>()
    val argument = message { every { car.drive(other.speed()) } returns true }
    if (argument.endsWith(HINT) || !argument.startsWith("no answer found for $other.speed()")) {
        return "Fail argument: $argument"
    }
    val afterChain = message { car.speed() }
    if (afterChain.endsWith(HINT) || !afterChain.startsWith("no answer found for $name.speed()")) {
        return "Fail after chain: $afterChain"
    }
    stubInChainedFile(car)
    if (!car.door(1).open() || !"${car.door(1)}".startsWith("Door(child of #")) return "Fail chained file"

    val door = mock<Door>()
    every { car.door(1) } returns door
    every { car.door(1).open() } returns true
    if (!car.door(1).open()) return "Fail stubbed head"
    return "OK"
}
