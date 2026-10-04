package sample

import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

enum class Side { LEFT, RIGHT }

interface Window {
    fun state(): String
}

interface Door {
    val window: Window
    fun open(): Boolean
}

interface Engine {
    fun maxSpeed(): Int
    fun identified(): Boolean
}

interface Car {
    val engine: Engine
    fun door(index: Int, side: Side = Side.LEFT): Door
    fun findDoor(index: Int): Door?
}

class Garage(val car: Car)

class Parts {
    val window = mock<Window>()
    val door = mock<Door>()
    val engine = mock<Engine>()
    val car = mock<Car>()

    init {
        every { window.state() } returns "up"
        every { door.window } returns window
        every { door.open() } returns true
        every { engine.maxSpeed() } returns 200
        every { engine.identified() } returns true
        every { car.engine } returns engine
        every { car.door(any(), any()) } returns door
        every { car.findDoor(any()) } returns door
    }
}

private const val CALL_SITES = "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true"

private fun failure(expected: String, block: () -> Unit): String? {
    val message = try {
        block()
        null
    } catch (e: AssertionError) {
        e.message
    }
    return if (message == expected) null else "Fail: expected <$expected>, but was <$message>"
}

fun box(): String {
    val chain = Parts()
    chain.car.door(1).window.state()
    verify { chain.car.door(1).window.state() }
    confirmVerified(chain.car, chain.door, chain.window)

    val headless = Parts()
    headless.door.window.state()
    failure("Verification failed: call 1 of 3: ${headless.car}.door(eq(1), eq(LEFT)) was not called") {
        verify { headless.car.door(1).window.state() }
    }?.let { return it }
    failure("Verification failed: call 1 of 2: ${headless.car}.findDoor(eq(1)) was not called") {
        verify { headless.car.findDoor(1)!!.window }
    }?.let { return it }
    verify { headless.door.window.state() }

    val defaults = Parts()
    defaults.car.door(2, Side.RIGHT).open()
    failure(
        "Verification failed: call 1 of 2: ${defaults.car}.door(eq(2), eq(LEFT)). " +
            "Only one matching call to ${defaults.car}/door happened, but arguments are not matching:\n" +
            "[0]: argument: 2, matcher: eq(2), result: +\n[1]: argument: RIGHT, matcher: eq(LEFT), result: -\n\n$CALL_SITES"
    ) { verify { defaults.car.door(2).open() } }?.let { return it }
    val order = mutableListOf<String>()
    fun side(): Side = Side.RIGHT.also { order += "side" }
    fun index(): Int = 2.also { order += "index" }
    verify { defaults.car.door(side = side(), index = index()).open() }
    if (order != listOf("side", "index")) return "Fail: order of link arguments $order"
    confirmVerified(defaults.car, defaults.door)

    val counts = Parts()
    counts.car.engine.maxSpeed()
    counts.car.engine.identified()
    verify(exactly = 1) { counts.car.engine.maxSpeed() }
    counts.car.door(1).open()
    verify(exactly = 0) { counts.car.door(1).window.state() }
    verify(inverse = true) { counts.car.door(1).window.state() }
    failure(
        "Verification failed: call 1 of 2: ${counts.car}.findDoor(eq(1)) was not called.\n\n" +
            "Calls to same mock:\n1) ${counts.car}.getEngine()\n2) ${counts.car}.getEngine()\n" +
            "3) ${counts.car}.door(1, LEFT)\n\n$CALL_SITES"
    ) { verify { counts.car.findDoor(1)!!.open() } }?.let { return it }
    verify(atLeast = 0, atMost = 1) { counts.car.findDoor(1)!!.open() }

    val plain = Parts()
    val garage = Garage(plain.car)
    val lazyCar = lazy { plain.car }
    plain.car.door(1).open()
    verify { garage.car.door(1).open() }
    verify { lazyCar.value.door(1).open() }
    confirmVerified(plain.car, plain.door)

    return "OK"
}
