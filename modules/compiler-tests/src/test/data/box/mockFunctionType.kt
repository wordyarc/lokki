package sample

import io.lokki.Runs
import io.lokki.every
import io.lokki.isMock
import io.lokki.just
import io.lokki.mock
import io.lokki.verify

fun box(): String {
    val supplier = mock<() -> Int>()
    if (supplier.javaClass.simpleName != "Function0\$LokkiMock\$6e72c36b\$MockFunctionTypeKt") {
        return "Fail name: ${supplier.javaClass.simpleName}"
    }
    if (Function0::class.java !in supplier.javaClass.interfaces) return "Fail interfaces"
    every { supplier() } returns 4
    if (supplier() != 4) return "Fail invoke"

    val formatter = mock<(Int) -> String>()
    every { formatter(any()) } answers { "n${firstArg<Int>()}" }
    if (listOf(1, 2).map(formatter) != listOf("n1", "n2")) return "Fail function argument"

    val length = mock<String.() -> Int>()
    every { length("x") } returns 2
    if ("x".length() != 2) return "Fail extension function type"

    val consumer = mock<(String) -> Unit>()
    every { consumer(any()) } just Runs
    consumer("a")
    verify { consumer("a") }

    val suspending = mock<suspend () -> Int>()
    if (!isMock(suspending)) return "Fail suspend function type"
    return "OK"
}
