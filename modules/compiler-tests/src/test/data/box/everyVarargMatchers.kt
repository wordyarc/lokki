package sample

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify

interface Route {
    fun stops(vararg names: String): Int
    fun legs(first: Int, vararg rest: Int, last: Int = -1): String
    fun codes(vararg codes: Any?): String
    fun flags(vararg flags: Boolean): String
    fun sum(values: IntArray): Int
}

fun MatcherScope.shortNames(): Array<String> = varargAll { it.length <= 2 }

fun MatcherScope.notVararg(): Array<String> = any()

private fun <T> answer(block: () -> T): Any? =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

fun box(): String {
    val route = mock<Route>(name = "route")

    every { route.stops("a", *anyVararg(), "z") } returns 1
    if (route.stops("a", "z") != 1) return "Fail: empty central part"
    if (route.stops("a", "b", "c", "z") != 1) return "Fail: central part"
    if (answer { route.stops("a") } != "none") return "Fail: fewer elements than the prefix and the postfix"
    if (answer { route.stops("b", "z") } != "none") return "Fail: another prefix"
    if (answer { route.stops("a", "b") } != "none") return "Fail: another postfix"

    every { route.stops(*varargAll { position > 0 && position < nArgs - 1 || it == "edge" }) } returns 2
    if (route.stops("edge", "b", "edge") != 2) return "Fail: position and nArgs"
    if (answer { route.stops("x", "b", "edge") } != "none") return "Fail: position of the first element"

    every { route.stops("any", *varargAny { it.startsWith("x") }) } returns 3
    if (route.stops("any", "a", "x1", "b") != 3) return "Fail: varargAny"
    if (answer { route.stops("any", "a", "b") } != "none") return "Fail: varargAny without a matching element"
    if (answer { route.stops("any") } != "none") return "Fail: varargAny without elements"

    val head = arrayOf("h1", "h2")
    every { route.stops(*head, *anyVararg(), more("m")) } returns 4
    if (route.stops("h1", "h2", "x", "n") != 4) return "Fail: spread array in the prefix"
    if (answer { route.stops("h1", "x", "n") } != "none") return "Fail: elements of the spread array"

    every { route.stops("short", *shortNames()) } returns 5
    if (route.stops("short", "a", "bc") != 5) return "Fail: vararg matcher of a helper"
    if (answer { route.stops("short", "abc") } != "none") return "Fail: predicate of a helper"

    val notVararg = try {
        every { route.stops("not", *notVararg()) } returns 6
        "registered"
    } catch (e: LokkiException) {
        e.message
    }
    val expected = "any() cannot be spread into a vararg parameter: only anyVararg, varargAll and varargAny match " +
        "a part of the array"
    if (notVararg != expected) return "Fail: helper without a vararg matcher: $notVararg"

    every { route.stops("assert", *varargAll { check(it.isNotEmpty()); throw AssertionError("rejected") }) } returns 7
    if (answer { route.stops("assert", "a") } != "none") return "Fail: assertion error of a predicate"

    every { route.legs(1, *anyIntVararg()) } returns "any"
    if (route.legs(1) != "any" || route.legs(1, 2, 3) != "any") return "Fail: anyIntVararg with a default"
    if (answer { route.legs(1, 2, last = 0) } != "none") return "Fail: default after anyIntVararg"

    every { route.legs(2, *varargAnyInt { it > 5 }, 0, last = 9) } returns "large"
    if (route.legs(2, 1, 6, 0, last = 9) != "large") return "Fail: varargAnyInt"
    if (answer { route.legs(2, 1, 5, 0, last = 9) } != "none") return "Fail: varargAnyInt without a match"

    every { route.legs(3, *varargAllInt { it % 2 == 0 }) } returns "even"
    if (route.legs(3, 2, 4) != "even" || route.legs(3) != "even") return "Fail: varargAllInt"
    if (answer { route.legs(3, 2, 3) } != "none") return "Fail: varargAllInt with an odd element"

    every { route.flags(true, *varargAllBoolean { !it }) } returns "flags"
    if (route.flags(true, false, false) != "flags") return "Fail: varargAllBoolean"

    every { route.codes(*anyVararg<String>()) } returns "strings"
    if (route.codes("a", null) != "strings") return "Fail: anyVararg of a type with null"
    if (answer { route.codes("a", 1) } != "none") return "Fail: anyVararg of another type"

    every { route.codes(0, *varargAll<String> { it.isNotEmpty() }) } returns "non-empty"
    if (route.codes(0, "a") != "non-empty") return "Fail: varargAll of a type"
    if (answer { route.codes(0, "a", null) } != "none") return "Fail: varargAll with null"
    if (answer { route.codes(0, 1) } != "none") return "Fail: varargAll of another type"

    every { route.codes(1, *varargAllNullable<String> { it == null }) } returns "nulls"
    if (route.codes(1, null, null) != "nulls") return "Fail: varargAllNullable"

    every { route.codes(2, *varargAnyNullable<Int> { it == null }) } returns "has null"
    if (route.codes(2, 1, null) != "has null") return "Fail: varargAnyNullable"

    every { route.sum(anyIntVararg()) } returns 10
    every { route.sum(varargAnyInt { it < 0 }) } returns -1
    if (route.sum(intArrayOf(1, 2)) != 10 || route.sum(intArrayOf(1, -2)) != -1) return "Fail: array parameter"

    val first = slot<String>()
    val last = slot<String>()
    every { route.stops("p", *anyVararg()) } returns 8
    if (route.stops("p", "q", "r", "s") != 8) return "Fail: prefix without a postfix"
    verify { route.stops("p", capture(first), *anyVararg(), capture(last)) }
    if (first.captured != "q" || last.captured != "s") return "Fail: capture around a vararg matcher"

    val message = try {
        route.stops("unknown")
        null
    } catch (e: LokkiException) {
        e.message
    }
    if (message == null || ".stops([eq(a), *anyVararg(), eq(z)])" !in message) return "Fail: description: $message"

    return "OK"
}
