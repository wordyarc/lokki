package sample

import io.lokki.LokkiException
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

    val answers = mapOf(
        "Car.drive" to noAnswer { car.drive(1) },
        "Car.park" to noAnswer { car.park() },
        "Car.<get-wheels>" to noAnswer { car.wheels },
        "Car.<get-driver>" to noAnswer { car.driver },
        "Car.<set-driver>" to noAnswer { car.driver = "me" },
        "Car.transport" to noAnswer { car.transport("box") },
        "Car.label" to noAnswer { with(car) { "x".label() } },
        "Car.describe" to noAnswer { car.describe() },
        "Car.compareTo" to noAnswer { car.compareTo(car) },
        "Car.refuel" to noAnswerSuspend { car.refuel(1.5) },
    )
    for ((member, message) in answers) {
        if (message != "no answer found for $member") return "Fail $member: $message"
    }

    if (car != car || car == mock<Car>()) return "Fail: equals is not identity"
    if (car.hashCode() != System.identityHashCode(car)) return "Fail: hashCode is not identity"
    if (!car.toString().startsWith(mockClass.name)) return "Fail toString: $car"

    return "OK"
}
