package sample

import io.lokki.every
import io.lokki.mock

interface Overloads {
    fun <T : CharSequence> pick(x: T): String
    fun <T : Number> pick(x: T): String
    fun take(x: Int): String
    fun take(x: Int?): String
    fun take(x: List<String>): String
    fun take(vararg x: String): String
}

fun box(): String {
    val overloads = mock<Overloads>()
    val nullable: Int? = 1

    every { overloads.pick("a") } returns "chars"
    every { overloads.pick(1) } returns "number"
    if (overloads.pick("a") != "chars" || overloads.pick(1) != "number") return "Fail: bounds of a type parameter"

    every { overloads.take(1) } returns "int"
    every { overloads.take(nullable) } returns "nullable"
    if (overloads.take(1) != "int" || overloads.take(nullable) != "nullable") return "Fail: nullability"

    every { overloads.take(listOf("a")) } returns "list"
    every { overloads.take("a") } returns "vararg"
    if (overloads.take(listOf("a")) != "list" || overloads.take("a") != "vararg") return "Fail: list and vararg"

    return "OK"
}
