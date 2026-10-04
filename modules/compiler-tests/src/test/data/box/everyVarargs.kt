package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

interface Route {
    fun stops(vararg names: String): Int
    fun legs(first: Int, vararg rest: Int, last: Int = -1): String
    fun codes(vararg codes: Any): String
}

private fun <T> answer(block: () -> T): Any? =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

fun box(): String {
    val route = mock<Route>()

    every { route.stops() } returns 0
    if (route.stops() != 0) return "Fail: no elements"
    if (answer { route.stops("a") } != "none") return "Fail: an element instead of none"

    every { route.stops(any(), "b") } returns 2
    if (route.stops("a", "b") != 2 || route.stops("x", "b") != 2) return "Fail: matcher and value"
    if (answer { route.stops("a", "c") } != "none") return "Fail: another value"
    if (answer { route.stops("a", "b", "c") } != "none") return "Fail: more elements"
    if (answer { route.stops("a") } != "none") return "Fail: fewer elements"

    val tail = arrayOf("y", "z")
    every { route.stops(any(), *tail) } returns 3
    if (route.stops("q", "y", "z") != 3) return "Fail: matcher and spread"
    if (answer { route.stops("q", "z", "y") } != "none") return "Fail: another order of spread elements"

    every { route.stops(*tail, "end") } returns 4
    if (route.stops("y", "z", "end") != 4) return "Fail: spread and value"

    every { route.legs(1, any(), more(5)) } returns "legs"
    if (route.legs(1, 0, 6) != "legs") return "Fail: matchers of primitive elements with a default"
    if (answer { route.legs(1, 0, 5) } != "none") return "Fail: another primitive element"
    if (answer { route.legs(1, 0, 6, last = 0) } != "none") return "Fail: default after matchers of elements"

    every { route.legs(2) } returns "no legs"
    if (route.legs(2) != "no legs") return "Fail: no elements and a default"
    if (answer { route.legs(2, 1) } != "none") return "Fail: an element instead of none with a default"

    every { route.codes(any<Int>(), any<String>()) } returns "typed"
    if (route.codes(1, "a") != "typed") return "Fail: types of elements"
    if (answer { route.codes("a", 1) } != "none") return "Fail: other types of elements"

    return "OK"
}
