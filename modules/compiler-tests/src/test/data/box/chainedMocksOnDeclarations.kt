package sample

import io.lokki.ChainedMocks
import io.lokki.LokkiException
import io.lokki.mock

interface Door {
    fun open(): Boolean
}

interface Car {
    fun door(): Door
}

@ChainedMocks
fun annotated(): Car = mock(relaxed = true)

fun plain(): Car = mock(relaxed = true)

class Garage {
    @ChainedMocks
    val car: Car = mock(relaxed = true)
}

@ChainedMocks
class Fleet {
    fun car(): Car = mock(relaxed = true)
}

private fun failure(block: () -> Any?): String? =
    try {
        block()
        null
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    if (annotated().door().open()) return "Fail: function"
    if (Garage().car.door().open()) return "Fail: property"
    if (Fleet().car().door().open()) return "Fail: class"
    @ChainedMocks
    val local = mock<Car>(relaxed = true)
    if (local.door().open()) return "Fail: local variable"

    val car = plain()
    val hint = "relaxed mock has no default for Door returned by $car.door(); stub the call or annotate the " +
        "declaration in chainedMocksOnDeclarations.kt that creates the mock with @ChainedMocks"
    val message = failure { car.door() }
    if (message != hint) return "Fail: a mock outside the annotated declarations: $message"
    if (annotated().javaClass == car.javaClass) return "Fail: one class for both"
    if (mock<Door>().javaClass != annotated().door().javaClass) return "Fail: a strict mock has its own class"
    return "OK"
}
