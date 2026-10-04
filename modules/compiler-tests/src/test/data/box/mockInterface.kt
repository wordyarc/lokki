package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Vehicle {
    val wheels: Int
    fun park()
}

interface Car : Vehicle, Comparable<Car> {
    var driver: String
    fun drive(distance: Int, fast: Boolean = false): String
    suspend fun refuel(liters: Double)
    fun <T : Any> transport(cargo: T): List<T>
    fun String.label(): String
    fun describe(): String = "car"
}

fun noAnswer(block: () -> Unit): String? =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message
    }

fun noAnswerSuspend(block: suspend () -> Unit): String? {
    var message: String? = "not completed"
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result ->
        message = when (val e = result.exceptionOrNull()) {
            null -> "no exception"
            is LokkiException -> e.message
            else -> throw e
        }
    })
    return message
}

fun box(): String {
    val car = mock<Car>()
    val mockClass = car.javaClass

    if (mockClass.name != "sample.Car\$LokkiMock\$08768170\$MockInterfaceKt") return "Fail name: ${mockClass.name}"
    if (Modifier.isPublic(mockClass.modifiers)) return "Fail: mock class is public"
    if (!Modifier.isFinal(mockClass.modifiers)) return "Fail: mock class is not final"
    if (mockClass.interfaces.map { it.name } != listOf("sample.Car", "io.lokki.internal.GeneratedMock")) {
        return "Fail: ${mockClass.interfaces.toList()}"
    }
    if (mock<Car>().javaClass != mockClass) return "Fail: second mock in the file has another class"

    if (!Regex("""Car\(#\d+\)""").matches(car.toString())) return "Fail toString: $car"
    val answers = mapOf(
        "drive(1, false)" to noAnswer { car.drive(1) },
        "park()" to noAnswer { car.park() },
        "getWheels()" to noAnswer { car.wheels },
        "getDriver()" to noAnswer { car.driver },
        "setDriver(me)" to noAnswer { car.driver = "me" },
        "transport(box)" to noAnswer { car.transport("box") },
        "label(x)" to noAnswer { with(car) { "x".label() } },
        "describe()" to noAnswer { car.describe() },
        "compareTo($car)" to noAnswer { car.compareTo(car) },
        "refuel(1.5)" to noAnswerSuspend { car.refuel(1.5) },
    )
    for ((call, message) in answers) {
        if (message != "no answer found for $car.$call among the configured answers: ()") return "Fail $call: $message"
    }

    every { car.drive(any()) } returns "driven"
    every { car.drive(more(5), true) } returns "far"
    val configured = "$car.drive(any(), eq(false))\n$car.drive(more(5), eq(true))"
    val unanswered = noAnswer { car.drive(1, true) }
    if (unanswered != "no answer found for $car.drive(1, true) among the configured answers: ($configured)") {
        return "Fail configured answers: $unanswered"
    }

    val state = mockClass.getDeclaredField("lokki\$state")
    val descriptor = mockClass.getDeclaredField("lokki\$descriptor")
    val getters = listOf(mockClass, mockClass.interfaces[1]).map { it.getDeclaredMethod("getLokki\$state") }
    if (!state.isSynthetic || !descriptor.isSynthetic) return "Fail: fields of the mock class are not synthetic"
    if (!getters.all { it.isSynthetic }) return "Fail: getter of the state is not synthetic"

    return "OK"
}
