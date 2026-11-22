package sample

import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyOrder
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Window {
    fun state(): String
}

interface Door {
    fun open(): Boolean
    fun close()
    fun knock(times: Int): Int
    fun window(index: Int): Window
}

interface Car {
    fun door(index: Int, side: String = "left"): Door
    suspend fun garage(level: Int): Car
    fun speed(): Int
}

fun <T> complete(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

private fun failure(block: () -> Unit): String =
    try {
        block()
        "no failure"
    } catch (e: AssertionError) {
        e.message!!
    }

fun box(): String {
    val car = mock<Car>()
    every { car.door(any()).open() } returns true
    every { car.door(any()).close() } just Runs
    val child = car.door(7)
    if (!child.open()) return "Fail any"
    if (car.door(8) !== child) return "Fail equivalent matchers"
    child.close()

    every { car.door(match { it > 100 }).knock(1) } returns 1
    if (car.door(200) !== child || car.door(7).knock(1) != 1) return "Fail lambda matcher"

    every { car.door(eq(1)).open() } returns false
    val one = car.door(1)
    if (one === child || one.open()) return "Fail eq matcher"
    every { car.door(1).knock(2) } returns 2
    if (car.door(1) !== one || one.knock(2) != 2) return "Fail value link after eq matcher"

    val deep = mock<Car>()
    every { deep.door(any(), any()).window(any()).state() } returns "UP"
    if (deep.door(3, "right").window(4).state() != "UP") return "Fail deep chain"

    val door = mock<Door>()
    val other = mock<Car>()
    every { other.door(1) } returns door
    every { other.door(any()).open() } returns true
    if (other.door(1) === door) return "Fail matcher link over a narrow head"

    verify { car.door(any()).open() }
    verifyOrder {
        car.door(7).open()
        car.door(any()).close()
    }
    verify(exactly = 0) { car.door(more(1000)).open() }
    val unmatched = failure { verify { car.door(more(1000)).close() } }
    if (!unmatched.startsWith("Verification failed: call 1 of 2: $car.door(more(1000), eq(left))")) {
        return "Fail verify of an unknown child: $unmatched"
    }

    val slot = slot<Int>()
    val captured = mock<Car>()
    every { captured.door(capture(slot)).open() } returns true
    captured.door(42).open()
    if (slot.captured != 42) return "Fail capture in link"

    coEvery { car.garage(any()).speed() } returns 9
    if (complete { car.garage(3) }.speed() != 9) return "Fail suspend link"

    excludeRecords { car.door(any()).knock(any()) }
    car.door(9).knock(1)
    verify(exactly = 0) { car.door(any()).knock(1) }
    verify { car.door(any()).knock(2) }
    verify(exactly = 0) { car.door(more(1)).knock(2) }

    val returned = mock<Car>()
    every { returned.door(1) } returns door
    every { door.open() } returns true
    returned.door(1).open()
    verify { returned.door(any()).open() }
    if (failure { verify(exactly = 0) { returned.door(any()).open() } } == "no failure") {
        return "Fail verify of a mock that a link with a matcher returned"
    }

    val relaxed = mock<Car>(relaxed = true)
    every { relaxed.door(any()).open() } returns true
    val relaxedChild = relaxed.door(9)
    if (!relaxedChild.open() || relaxedChild.knock(3) != 0) return "Fail relaxed child"
    relaxedChild.close()
    return "OK"
}
