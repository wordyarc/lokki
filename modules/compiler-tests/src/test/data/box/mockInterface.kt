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
    if (Modifier.isFinal(mockClass.modifiers)) return "Fail: mock class is final"
    mockClass.declaredMethods.find { Modifier.isFinal(it.modifiers) && !Modifier.isPrivate(it.modifiers) }?.let {
        return "Fail: final member $it"
    }
    if (mockClass.interfaces.map { it.name } != listOf("sample.Car", "io.lokki.internal.GeneratedMock")) {
        return "Fail: ${mockClass.interfaces.toList()}"
    }
    if (mock<Car>().javaClass != mockClass) return "Fail: second mock in the file has another class"

    if (!Regex("""Car\(#\d+\)""").matches(car.toString())) return "Fail toString: $car"
    val answers = listOf(
        Triple("drive(1, false)", "Car.drive(Int, Boolean): String", noAnswer { car.drive(1) }),
        Triple("park()", "Vehicle.park(): Unit", noAnswer { car.park() }),
        Triple("getWheels()", "Vehicle.getWheels(): Int", noAnswer { car.wheels }),
        Triple("getDriver()", "Car.getDriver(): String", noAnswer { car.driver }),
        Triple("setDriver(me)", "Car.setDriver(String): Unit", noAnswer { car.driver = "me" }),
        Triple("transport(box)", "Car.transport(Any): List", noAnswer { car.transport("box") }),
        Triple("label(x)", "Car.label(String): String", noAnswer { with(car) { "x".label() } }),
        Triple("describe()", "Car.describe(): String", noAnswer { car.describe() }),
        Triple("compareTo($car)", "Comparable.compareTo(Car): Int", noAnswer { car.compareTo(car) }),
        Triple("refuel(1.5)", "Car.refuel(Double): Unit", noAnswerSuspend { car.refuel(1.5) }),
    )
    for ((call, member, message) in answers) {
        val expected = "no answer found for $car.$call among the configured answers: ()\nCalled member: $member"
        if (message != expected) return "Fail $call: $message"
    }

    every { car.drive(any()) } returns "driven"
    every { car.drive(more(5), true) } returns "far"
    val configured = "$car.drive(any(), eq(false))\n$car.drive(more(5), eq(true))"
    val unanswered = noAnswer { car.drive(1, true) }
    val drive = "Called member: Car.drive(Int, Boolean): String"
    if (unanswered != "no answer found for $car.drive(1, true) among the configured answers: ($configured)\n$drive") {
        return "Fail configured answers: $unanswered"
    }

    val state = mockClass.getDeclaredField("lokki\$state")
    val descriptor = mockClass.getDeclaredField("lokki\$descriptor")
    val accessors = listOf(mockClass, mockClass.interfaces[1]).map { it.getDeclaredMethod("lokki\$state") }
    if (!state.isSynthetic || !descriptor.isSynthetic) return "Fail: fields of the mock class are not synthetic"
    if (!Modifier.isFinal(state.modifiers) || Modifier.isVolatile(state.modifiers)) return "Fail: state field is not final"
    if (accessors.any { it.isSynthetic }) return "Fail: the state accessor is synthetic"
    if (mockClass.methods.any { it.name.startsWith("getLokki") }) return "Fail: the mock has a getter of lokki state"

    return "OK"
}
