package sample

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify

interface Store {
    fun names(names: Array<String>): Int
    fun codes(codes: IntArray): String
    fun value(value: Any?): String
    fun rows(rows: Array<IntArray>): Int
}

fun MatcherScope.startsWith(first: String): Array<String> = array(eq(first), any())

fun MatcherScope.valueElement(): Array<String> = array("a", any())

private fun <T> answer(block: () -> T): Any? =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val store = mock<Store>()

    every { store.names(array(eq("a"), any())) } returns 1
    if (store.names(arrayOf("a", "b")) != 1) return "Fail: elements"
    if (answer { store.names(arrayOf("b", "b")) } != "none") return "Fail: another element"
    if (answer { store.names(arrayOf("a")) } != "none") return "Fail: another size"
    val strict = failure { store.names(arrayOf("c")) }
    if ("Store.names([eq(a), any()])" !in strict) return "Fail: description: $strict"

    every { store.names(array("x", more("m"))) } returns 2
    if (store.names(arrayOf("x", "n")) != 2) return "Fail: value element"
    if (answer { store.names(arrayOf("x", "a")) } != "none") return "Fail: matcher after a value element"

    val head = arrayOf("p", "q")
    every { store.names(array(*head, any())) } returns 3
    if (store.names(arrayOf("p", "q", "r")) != 3) return "Fail: spread values"
    if (answer { store.names(arrayOf("p", "r")) } != "none") return "Fail: elements of spread values"

    every { store.codes(array(1, *anyVararg<Int>(), 9)) } returns "vararg"
    if (store.codes(intArrayOf(1, 9)) != "vararg") return "Fail: empty central part"
    if (store.codes(intArrayOf(1, 5, 6, 9)) != "vararg") return "Fail: central part"
    if (answer { store.codes(intArrayOf(2, 9)) } != "none") return "Fail: prefix of a vararg matcher"

    every { store.codes(array()) } returns "empty"
    if (store.codes(IntArray(0)) != "empty") return "Fail: empty array"

    every { store.value(array<IntArray>(any(), any())) } returns "ints"
    if (store.value(intArrayOf(1, 2)) != "ints") return "Fail: typed array"
    if (answer { store.value(arrayOf(1, 2)) } != "none") return "Fail: array of another type"

    every { store.rows(array(array(1), any())) } returns 4
    if (store.rows(arrayOf(intArrayOf(1), intArrayOf())) != 4) return "Fail: nested array"
    if (answer { store.rows(arrayOf(intArrayOf(2), intArrayOf())) } != "none") return "Fail: element of a nested array"

    val slot = slot<Int>()
    every { store.codes(array(capture(slot), 7)) } returns "captured"
    if (store.codes(intArrayOf(5, 7)) != "captured" || slot.captured != 5) return "Fail: capture in an element"
    if (answer { store.codes(intArrayOf(6, 8)) } != "none" || slot.captured != 5) return "Fail: capture of a mismatch"

    every { store.names(startsWith("h")) } returns 5
    if (store.names(arrayOf("h", "z")) != 5) return "Fail: array of a helper"

    val helper = failure { every { store.names(valueElement()) } returns 6 }
    if (helper != "an element of array in a matcher helper is not a matcher") return "Fail: value in a helper: $helper"

    verify { store.names(array(eq("a"), any())) }
    verify(exactly = 0) { store.names(array(eq("z"), any())) }

    return "OK"
}
