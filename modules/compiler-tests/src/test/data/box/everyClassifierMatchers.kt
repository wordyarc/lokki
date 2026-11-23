package sample

import io.lokki.Matcher
import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.reflect.KClass

interface Sink {
    fun take(value: Any?): String
}

private fun <T : Any> MatcherScope.anyOf(type: KClass<T>): T = any(type)

private class Nullish<T : Any> : Matcher<T> {
    override fun match(arg: T?): Boolean = arg == null

    override fun toString(): String = "nullish()"
}

private fun <T : Any> MatcherScope.nullish(type: KClass<T>): T = match(Nullish(), type)

fun box(): String {
    val sink = mock<Sink>()
    every { sink.take(any()) } returns "other"
    every { sink.take(anyOf(String::class)) } returns "string"
    every { sink.take(any(Int::class)) } returns "int"
    if (sink.take("a") != "string") return "Fail helper with KClass"
    if (sink.take(1) != "int") return "Fail any(KClass)"
    if (sink.take(1.0) != "other") return "Fail type check"

    val nulls = mock<Sink>()
    every { nulls.take(any()) } returns "other"
    every { nulls.take(nullish(String::class)) } returns "null"
    if (nulls.take(null) != "null") return "Fail custom matcher does not see null"
    if (nulls.take("a") != "other") return "Fail custom matcher"
    if (nulls.take(1) != "other") return "Fail wrong type reached custom matcher"
    verify { nulls.take(match(Nullish<String>())) }
    return "OK"
}
