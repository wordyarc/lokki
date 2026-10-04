package sample

import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock

interface Shelf {
    var label: String
    fun find(id: Int): String
    fun findOrNull(id: Int): String?
    operator fun get(index: Int): String
    operator fun set(index: Int, book: String)
    operator fun plusAssign(book: String)
    operator fun contains(book: String): Boolean
    operator fun invoke(book: String): Int
}

fun box(): String {
    val shelf = mock<Shelf>()
    val nullable: Shelf? = shelf

    every { shelf.findOrNull(1)!! } returns "not null"
    if (shelf.findOrNull(1) != "not null") return "Fail: not-null assertion"

    every { nullable!!.find(2) } returns "receiver"
    if (shelf.find(2) != "receiver") return "Fail: not-null assertion of the receiver"

    every { shelf.find(3) as CharSequence } returns "cast"
    if (shelf.find(3) != "cast") return "Fail: cast"

    every { return@every shelf.find(4) } returns "return"
    if (shelf.find(4) != "return") return "Fail: return"

    every(stubBlock = { shelf.find(5) }) returns "named"
    if (shelf.find(5) != "named") return "Fail: named block"

    every(fun MatcherScope.(): String { return shelf.find(more(5)) }) returns "anonymous"
    if (shelf.find(6) != "anonymous") return "Fail: anonymous function"

    every { shelf[any()] } returns "indexed"
    if (shelf[7] != "indexed") return "Fail: get"

    val log = ArrayList<String>()
    every { shelf[1] = any() } answers { log += "set ${secondArg<String>()}" }
    shelf[1] = "a"

    every { shelf += any() } answers { log += "plusAssign ${firstArg<String>()}" }
    shelf += "b"

    every { shelf.label = any() } answers { log += "label ${firstArg<String>()}" }
    shelf.label = "c"
    if (log != listOf("set a", "plusAssign b", "label c")) return "Fail: assignments $log"

    every { shelf.label } returns "d"
    if (shelf.label != "d") return "Fail: property"

    every { "e" in shelf } returns true
    if ("e" !in shelf) return "Fail: contains"

    every { shelf("f") } returns 6
    if (shelf("f") != 6) return "Fail: invoke"

    return "OK"
}
