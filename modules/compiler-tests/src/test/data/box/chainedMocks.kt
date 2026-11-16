@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.clearMocks
import io.lokki.coEvery
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifySequence
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Window {
    fun state(): String
}

interface Door {
    fun open(): Boolean
    fun close()
    fun window(index: Int): Window
}

open class Wheel {
    open fun spin(): Int = 0
}

open class Locked private constructor() {
    open fun open(): Boolean = false
}

interface Engine {
    val power: Int
}

interface Car {
    fun door(index: Int, side: String = "left"): Door
    fun wheel(): Wheel
    fun locked(): Locked
    val engine: Engine
    fun items(): List<String>
    suspend fun garage(): Car
    fun speed(): Int
    val onStart: () -> Unit
}

fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

private fun message(block: () -> Unit): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!
    } catch (e: AssertionError) {
        "AssertionError: ${e.message}"
    }

fun box(): String {
    val car = mock<Car>()
    val name = "$car".removePrefix("Car(").removeSuffix(")")

    every { car.door(1).open() } returns true
    every { car.door(1).close() } just Runs
    every { car.door(2).open() } returns false
    val first = car.door(1)
    if (!first.open() || car.door(2).open()) return "Fail open"
    if (car.door(1) !== first || car.door(2) === first) return "Fail child identity"
    if (!"$first".startsWith("Door(child of $name#")) return "Fail child name: $first"
    first.close()

    every { car.door(1, "right").window(2).state() } returns "UP"
    if (car.door(1, "right").window(2).state() != "UP") return "Fail deep chain"
    if (car.door(1, "right") === first) return "Fail default argument"
    val window = "${car.door(1, "right").window(2)}"
    if (!window.startsWith("Window(child^2 of $name#")) return "Fail grandchild name: $window"

    val door = mock<Door>()
    every { car.door(3) } returns door
    every { car.door(3).open() } returns true
    if (car.door(3) !== door || !door.open()) return "Fail stubbed head"

    every { car.wheel().spin() } returns 7
    if (car.wheel().spin() != 7) return "Fail class child"
    every { car.engine.power } returns 100
    if (car.engine.power != 100) return "Fail property link"
    every { car.items()[0] } returns "first"
    if (car.items()[0] != "first") return "Fail generic child"
    every { car.onStart() } just Runs
    car.onStart()

    coEvery { car.garage().speed() } returns 5
    if (complete { car.garage() }.speed() != 5) return "Fail suspend link"

    val locked = message { every { car.locked().open() } returns true }
    val lockedHint = "\nThe call is a link of a call chain in a stubbed or verified call: stub it first, " +
        "a child mock cannot be created: 'sample.Locked' cannot be mocked: none of its constructors is visible " +
        "from this file"
    if (!locked.startsWith("no answer found for $car.locked()") || !locked.endsWith(lockedHint)) {
        return "Fail unmockable link: $locked"
    }

    verify { car.door(1).open() }
    verify { car.door(1, "right").window(2).state() }
    verify(exactly = 0) { car.door(2).close() }
    val notCalled = message { verify { car.door(4).open() } }
    val unmatched = "AssertionError: Verification failed: call 1 of 2: $car.door(eq(4), eq(left)). No matching calls"
    if (!notCalled.startsWith(unmatched)) {
        return "Fail verify without child: $notCalled"
    }
    verify(exactly = 0) { car.door(4).open() }

    val sequence = mock<Car>()
    every { sequence.door(1).window(1).state() } returns "1"
    every { sequence.door(1).window(2).state() } returns "2"
    sequence.door(1).window(1).state()
    sequence.door(1).window(2).state()
    verifySequence {
        sequence.door(1).window(1).state()
        sequence.door(1).window(2).state()
    }

    val excluded = mock<Car>()
    excludeRecords { excluded.door(5).open() }
    every { excluded.door(5).open() } returns true
    if (!excluded.door(5).open()) return "Fail stub after exclusion"
    verify(exactly = 0) { excluded.door(5).open() }

    val relaxed = mock<Car>(relaxed = true)
    every { relaxed.items()[1] } returns "second"
    if (relaxed.items()[1] != "second") return "Fail relaxed link"

    clearMocks(car, answers = false, recordedCalls = false, verificationMarks = false, exclusionRules = false)
    every { car.door(1).open() } returns false
    if (car.door(1) !== first) return "Fail child after clearing other state"
    clearMocks(car, answers = true, recordedCalls = false, verificationMarks = false, exclusionRules = false)
    every { car.door(1).open() } returns false
    if (car.door(1) === first || car.door(1).open()) return "Fail child after clearMocks"
    return "OK"
}
