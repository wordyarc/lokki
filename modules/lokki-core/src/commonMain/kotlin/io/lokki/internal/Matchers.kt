package io.lokki.internal

import io.lokki.Matcher

@PublishedApi
internal object AnyMatcher : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = true
}

@PublishedApi
internal class EqMatcher(private val value: Any?) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = deepEquals(arg, value)
}

private fun deepEquals(a: Any?, b: Any?): Boolean =
    when (a) {
        is Array<*> -> b is Array<*> && a.contentDeepEquals(b)
        is BooleanArray -> b is BooleanArray && a.contentEquals(b)
        is ByteArray -> b is ByteArray && a.contentEquals(b)
        is CharArray -> b is CharArray && a.contentEquals(b)
        is ShortArray -> b is ShortArray && a.contentEquals(b)
        is IntArray -> b is IntArray && a.contentEquals(b)
        is LongArray -> b is LongArray && a.contentEquals(b)
        is FloatArray -> b is FloatArray && a.contentEquals(b)
        is DoubleArray -> b is DoubleArray && a.contentEquals(b)
        else -> a == b
    }
