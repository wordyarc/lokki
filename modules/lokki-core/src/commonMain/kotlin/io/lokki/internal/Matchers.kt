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

@PublishedApi
internal class ArrayMatcher(private val elements: Array<Matcher<Any?>>) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean {
        val actual = arg.arrayElements() ?: return false
        return actual.size == elements.size && elements.indices.all { elements[it].match(actual[it]) }
    }
}

@PublishedApi
internal object Matchers {
    fun eq(value: Any?, inverse: Boolean = false): Matcher<Any?> = EqMatcher(value).inverseIf(inverse)

    fun neq(value: Any?): Matcher<Any?> = eq(value, inverse = true)

    fun refEq(value: Any?, inverse: Boolean = false): Matcher<Any?> = Matcher<Any?> { it === value }.inverseIf(inverse)

    fun nrefEq(value: Any?): Matcher<Any?> = refEq(value, inverse = true)

    fun isNull(inverse: Boolean = false): Matcher<Any?> = Matcher<Any?> { it == null }.inverseIf(inverse)

    fun cmpEq(value: Comparable<*>): Matcher<Any?> = comparing(value) { it == 0 }

    fun more(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        comparing(value) { it > 0 || andEquals && it == 0 }

    fun less(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        comparing(value) { it < 0 || andEquals && it == 0 }

    fun range(
        from: Comparable<*>,
        to: Comparable<*>,
        fromInclusive: Boolean = true,
        toInclusive: Boolean = true,
    ): Matcher<Any?> = and(more(from, fromInclusive), less(to, toInclusive))

    fun and(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> = Matcher { left.match(it) && right.match(it) }

    fun or(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> = Matcher { left.match(it) || right.match(it) }

    fun not(value: Matcher<Any?>): Matcher<Any?> = Matcher { !value.match(it) }

    @Suppress("UNCHECKED_CAST")
    fun match(matcher: (Nothing) -> Boolean): Matcher<Any?> {
        val predicate = matcher as (Any?) -> Boolean
        return Matcher { it != null && predicate(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun matchNullable(matcher: (Nothing?) -> Boolean): Matcher<Any?> = Matcher(matcher as (Any?) -> Boolean)

    inline fun <reified T> ofType(): Matcher<Any?> = Matcher { it is T }

    inline fun <reified T> typed(matcher: Matcher<Any?>): Matcher<Any?> =
        Matcher { (it == null || it is T) && matcher.match(it) }

    fun spread(array: Any): Array<Matcher<Any?>> {
        val elements = array.arrayElements()!!
        return Array(elements.size) { EqMatcher(elements[it]) }
    }
}

private fun Matcher<Any?>.inverseIf(inverse: Boolean): Matcher<Any?> = if (inverse) Matchers.not(this) else this

@Suppress("UNCHECKED_CAST")
private inline fun comparing(value: Comparable<*>, crossinline matches: (Int) -> Boolean): Matcher<Any?> =
    Matcher { it != null && matches((it as Comparable<Any?>).compareTo(value)) }

private fun Any?.arrayElements(): List<Any?>? =
    when (this) {
        is Array<*> -> asList()
        is BooleanArray -> asList()
        is ByteArray -> asList()
        is CharArray -> asList()
        is ShortArray -> asList()
        is IntArray -> asList()
        is LongArray -> asList()
        is FloatArray -> asList()
        is DoubleArray -> asList()
        else -> null
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
