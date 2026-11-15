package sample

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyAll
import io.lokki.verifyOrder
import io.lokki.verifySequence
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Door {
    fun open(): Boolean
}

interface Car {
    fun door(index: Int, side: String = "left"): Door
    fun spare(door: Door): Door
}

interface Garage {
    suspend fun car(level: Int): Car
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

private fun failure(expected: String, block: () -> Unit): String? {
    val message = try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }
    return if (message == expected) null else "Fail: expected <$expected>, but was <$message>"
}

private fun fails(block: () -> Unit): Boolean =
    try {
        block()
        false
    } catch (e: AssertionError) {
        true
    }

private fun door(): Door = mock<Door>().also { every { it.open() } returns true }

private fun car(): Car = mock<Car>().also { every { it.door(any(), any()) } answers { door() } }

fun box(): String {
    val first = door()
    val second = door()
    val car = mock<Car>()
    every { car.door(1) } returnsMany listOf(first, second)
    car.door(1).open()

    verify { car.door(1).open() }
    verify(exactly = 1) { car.door(1).open() }
    verifyAll { car.door(1).open() }
    verifyOrder { car.door(1).open() }
    verifySequence { car.door(1).open() }
    failure(
        "Verification failed: call 1 of 1: $second.open() should not be called\n\n" +
            "Calls:\n1) $first.open()\n\n$CALL_SITES"
    ) { verify(exactly = 0) { car.door(1).open() } }?.let { return it }
    failure("Inverse verification failed.\n\nVerified calls:\n1) $first.open()") {
        verify(inverse = true) { car.door(1).open() }
    }?.let { return it }
    confirmVerified(car, first, second)

    car.door(1).open()
    verify(exactly = 2) { car.door(1).open() }
    failure(
        "Verification failed: call 2 of 2: $second.open(). 2 matching calls found, but needs exactly 1 calls\n" +
            "Calls:\n1) $first.open()\n2) $second.open()\n\n$CALL_SITES"
    ) { verify(exactly = 1) { car.door(1).open() } }?.let { return it }

    val answered = car()
    answered.door(1).open()
    answered.door(2)
    verify { answered.door(1).open() }
    verify(exactly = 0) { answered.door(2).open() }
    if (!fails { verify(exactly = 0) { answered.door(1).open() } }) return "Fail: a door of a computed answer"
    if (!fails { verify { answered.door(2).open() } }) return "Fail: a door that was not opened"

    val spare = mock<Car>()
    every { spare.spare(any()) } returnsArgument 0
    spare.spare(first).open()
    verify { spare.spare(first).open() }
    if (!fails { verify(exactly = 0) { spare.spare(first).open() } }) return "Fail: a door returned as an argument"

    val garage = mock<Garage>()
    val parked = car()
    coEvery { garage.car(1) } returnsMany listOf(parked, car())
    coEvery { garage.car(2) } coAnswers { car() }
    complete { garage.car(1).door(1).open() }
    complete { garage.car(2).door(1).open() }
    coVerify { garage.car(1).door(1).open() }
    coVerify { garage.car(2).door(1).open() }
    if (!fails { coVerify(exactly = 0) { garage.car(1).door(1).open() } }) return "Fail: a suspend link"
    if (!fails { coVerify(exactly = 0) { garage.car(2).door(1).open() } }) return "Fail: a suspending answer of a link"
    coVerify(exactly = 0) { garage.car(1).door(2).open() }

    return "OK"
}
