// MODULE: lib
// FILE: Helpers.kt
package lib

import io.lokki.Matcher
import io.lokki.MatcherScope

class EndsWith(private val suffix: String) : Matcher<String> {
    override fun match(arg: String): Boolean = arg.endsWith(suffix)
}

fun MatcherScope.between(from: Int, to: Int): Int = and(more(from, andEquals = true), less(to, andEquals = true))

fun MatcherScope.word(): String = and(match { it.isNotEmpty() }, not(match { ' ' in it }))

fun MatcherScope.endsWith(suffix: String): String = match(EndsWith(suffix))

fun MatcherScope.five(): Int = 5

fun MatcherScope.two(): Int {
    any<Int>()
    return any()
}

fun MatcherScope.valueOperand(): Int = and(1, more(0))

// MODULE: main(lib)
// FILE: main.kt
package app

import io.lokki.LokkiException
import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock
import lib.EndsWith
import lib.between
import lib.endsWith
import lib.five
import lib.two
import lib.valueOperand
import lib.word

interface Api {
    fun find(id: Int, name: String): String
    fun page(offset: Int, size: Int = 10): String
    fun sum(vararg values: Int): String
}

context(scope: MatcherScope)
fun positive(): Int = scope.more(0)

fun shortText(scope: MatcherScope): String = scope.match { it.length < 3 }

val MatcherScope.negative: Int
    get() = less(0)

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        e.message!!
    }

private const val NONE = "no answer found for Api.find"

fun helpers(): String? {
    val api = mock<Api>()

    every { api.find(between(1, 3), "receiver") } returns "between"
    if ((0..4).map { answer { api.find(it, "receiver") } } != listOf(NONE, "between", "between", "between", NONE)) {
        return "helper with the scope as a receiver"
    }

    every { api.find(positive(), "context") } returns "positive"
    if (api.find(1, "context") != "positive" || answer { api.find(0, "context") } != NONE) {
        return "helper with the scope as a context"
    }

    every { api.find(10, shortText(this)) } returns "short"
    if (api.find(10, "ab") != "short" || answer { api.find(10, "abc") } != NONE) return "helper with the scope as an argument"

    every { api.find(negative, "property") } returns "negative"
    if (api.find(-1, "property") != "negative" || answer { api.find(0, "property") } != NONE) return "property helper"

    every { api.find(five(), "value") } returns "five"
    if (api.find(5, "value") != "five" || answer { api.find(6, "value") } != NONE) return "helper without matchers"

    every { api.find(20, word()) } returns "word"
    if (api.find(20, "ab") != "word") return "helper of a reference type"
    if (answer { api.find(20, "a b") } != NONE || answer { api.find(20, "") } != NONE) return "composite of a reference type"

    every { api.find(30, endsWith("x")) } returns "helper matcher"
    every { api.find(40, match(EndsWith("y"))) } returns "matcher"
    if (api.find(30, "ax") != "helper matcher" || answer { api.find(30, "a") } != NONE) return "matcher object in a helper"
    if (api.find(40, "ay") != "matcher" || answer { api.find(40, "a") } != NONE) return "matcher object"

    every { api.find(and(between(50, 59), not(eq(55))), any()) } returns "operand"
    if (api.find(51, "a") != "operand" || answer { api.find(55, "a") } != NONE) return "helper as an operand"

    every { api.sum(between(1, 2), 7, positive()) } returns "elements"
    if (api.sum(2, 7, 1) != "elements" || answer { api.sum(3, 7, 1) } != "no answer found for Api.sum") {
        return "helpers as vararg elements"
    }

    every { api.page(between(100, 200)) } returns "page"
    if (api.page(150) != "page" || answer { api.page(150, 11) } != "no answer found for Api.page") {
        return "helper with an omitted argument"
    }

    if (answer { every { api.find(two(), "a") } returns "two"; "registered" } != "matcher helper produced 2 matchers") {
        return "helper with two matchers"
    }
    val message = answer { every { api.find(valueOperand(), "a") } returns "operand"; "registered" }
    if (message != "an operand of and, or, not in a matcher helper is not a matcher") return "value operand in a helper"

    return null
}

fun allAny(): String? {
    val api = mock<Api>()

    every { api.find(allAny(), "ignored") } returns "all"
    if (api.find(1, "a") != "all" || api.find(2, "b") != "all") return "allAny replaces values"

    every { api.page(allAny()) } returns "any page"
    if (api.page(1) != "any page" || api.page(2, 99) != "any page") return "allAny replaces omitted arguments"

    val other = mock<Api>()
    every { other.find(allAny(), eq("kept")) } returns "kept"
    if (other.find(1, "kept") != "kept" || answer { other.find(1, "other") } != NONE) return "allAny keeps matchers"

    every { other.find(7, allAny()) } returns "seven"
    if (other.find(7, "a") != "seven" || answer { other.find(8, "a") } != NONE) return "allAny is any in another position"

    every { other.sum(allAny(), 5) } returns "sum"
    if (other.sum(1, 2) != "sum" || answer { other.sum(1, 2, 3) } != "no answer found for Api.sum") {
        return "allAny as the first vararg element"
    }

    return null
}

fun box(): String {
    val failure = helpers() ?: allAny()
    return if (failure == null) "OK" else "Fail: $failure"
}
