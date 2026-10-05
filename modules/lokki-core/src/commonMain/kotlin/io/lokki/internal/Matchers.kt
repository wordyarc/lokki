package io.lokki.internal

import io.lokki.AssertScope
import io.lokki.CapturingSlot
import io.lokki.Matcher
import kotlin.reflect.KClass

@PublishedApi
internal object AnyMatcher : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = true

    override fun toString(): String = "any()"
}

@PublishedApi
internal class EqMatcher(private val value: Any?) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = deepEquals(arg, value)

    override fun toString(): String = "eq(${value.toStr()})"
}

@PublishedApi
internal class ArrayMatcher(private val elements: Array<Matcher<Any?>>) :
    Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean {
        val actual = arg.arrayElements() ?: return false
        return actual.size == elements.size && elements.indices.all { elements[it].match(actual[it], errors) }
    }

    override fun capture(arg: Any?) {
        val actual = arg.arrayElements() ?: return
        elements.forEachIndexed { index, element -> (element as? CapturingMatcher)?.capture(actual[index]) }
    }

    override fun toString(): String = elements.joinToString(prefix = "[", postfix = "]")
}

@PublishedApi
internal object Matchers {
    fun eq(value: Any?, inverse: Boolean = false): Matcher<Any?> =
        if (inverse) ValueMatcher("nonEq", value) { !deepEquals(it, value) } else EqMatcher(value)

    fun neq(value: Any?): Matcher<Any?> = eq(value, inverse = true)

    fun refEq(value: Any?, inverse: Boolean = false): Matcher<Any?> =
        if (inverse) ValueMatcher("refNonEq", value) { it !== value } else ValueMatcher("refEq", value) { it === value }

    fun nrefEq(value: Any?): Matcher<Any?> = refEq(value, inverse = true)

    fun isNull(inverse: Boolean = false): Matcher<Any?> =
        if (inverse) named("notNull()") { it != null } else named("null()") { it == null }

    fun cmpEq(value: Comparable<*>): Matcher<Any?> = comparing("cmpEq", value) { it == 0 }

    fun more(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        if (andEquals) comparing("moreAndEquals", value) { it >= 0 } else comparing("more", value) { it > 0 }

    fun less(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        if (andEquals) comparing("lessAndEquals", value) { it <= 0 } else comparing("less", value) { it < 0 }

    fun range(
        from: Comparable<*>,
        to: Comparable<*>,
        fromInclusive: Boolean = true,
        toInclusive: Boolean = true,
    ): Matcher<Any?> = and(more(from, fromInclusive), less(to, toInclusive))

    fun and(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("and", arrayOf(left, right)) { arg, errors ->
            left.match(arg, errors) && right.match(arg, errors)
        }

    fun or(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("or", arrayOf(left, right)) { arg, errors ->
            left.match(arg, errors) || right.match(arg, errors)
        }

    fun not(value: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("not", arrayOf(value)) { arg, errors -> !value.match(arg, errors) }

    @Suppress("UNCHECKED_CAST")
    fun match(matcher: (Nothing) -> Boolean): Matcher<Any?> {
        val predicate = matcher as (Any?) -> Boolean
        return FunctionMatcher { it != null && predicate(it) }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> match(matcher: Matcher<T>): Matcher<Any?> = filtered(matcher as Matcher<Any?>) { it is T }

    @Suppress("UNCHECKED_CAST")
    fun matchNullable(matcher: (Nothing?) -> Boolean): Matcher<Any?> = FunctionMatcher(matcher as (Any?) -> Boolean)

    @Suppress("UNCHECKED_CAST")
    fun withArg(captureBlock: AssertScope.(Nothing) -> Unit): Matcher<Any?> =
        match(asserting(captureBlock as AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun withNullableArg(captureBlock: AssertScope.(Nothing?) -> Unit): Matcher<Any?> =
        matchNullable(asserting(captureBlock as AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun capture(lst: MutableList<*>): Matcher<Any?> =
        CaptureMatcher(slot = false) { if (it != null) (lst as MutableList<Any?>).add(it) }

    @Suppress("UNCHECKED_CAST")
    fun capture(lst: CapturingSlot<*>): Matcher<Any?> =
        CaptureMatcher(slot = true) { if (it != null) (lst as CapturingSlot<Any?>).captured = it }

    @Suppress("UNCHECKED_CAST")
    fun captureNullable(lst: MutableList<*>): Matcher<Any?> =
        CaptureMatcher(slot = false) { (lst as MutableList<Any?>).add(it) }

    @Suppress("UNCHECKED_CAST")
    fun captureNullable(lst: CapturingSlot<*>): Matcher<Any?> =
        CaptureMatcher(slot = true) { (lst as CapturingSlot<Any?>).captured = it }

    inline fun <reified T> ofType(): Matcher<Any?> = named("ofType(${T::class.simpleName})") { it is T }

    inline fun <reified T> typed(matcher: Matcher<Any?>): Matcher<Any?> = filtered(matcher) { it == null || it is T }

    fun filtered(matcher: Matcher<Any?>, accepts: (Any?) -> Boolean): Matcher<Any?> = FilteredMatcher(matcher, accepts)

    fun named(name: String, matches: (Any?) -> Boolean): Matcher<Any?> = NamedMatcher(name, matches)

    fun spread(array: Any): Array<Matcher<Any?>> {
        val elements = array.arrayElements()!!
        return Array(elements.size) { EqMatcher(elements[it]) }
    }
}

internal interface CapturingMatcher {
    fun capture(arg: Any?)
}

internal interface ReportingMatcher {
    fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean
}

internal fun Matcher<Any?>.match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
    if (this is ReportingMatcher) match(arg, errors) else match(arg)

internal fun Array<Matcher<Any?>>.match(arguments: Array<Any?>): Boolean {
    for (index in indices) {
        if (!this[index].match(arguments[index])) return false
    }
    return true
}

internal fun Array<Matcher<Any?>>.capture(arguments: Array<Any?>) {
    for (index in indices) {
        (this[index] as? CapturingMatcher)?.capture(arguments[index])
    }
}

internal val Matcher<Any?>.capturesIntoSlot: Boolean
    get() = when (this) {
        is CaptureMatcher -> slot
        is FilteredMatcher -> matcher.capturesIntoSlot
        else -> false
    }

private class NamedMatcher(private val name: String, private val matches: (Any?) -> Boolean) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = matches(arg)

    override fun toString(): String = name
}

private class FunctionMatcher(private val predicate: (Any?) -> Boolean) : Matcher<Any?>, ReportingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
        try {
            predicate(arg)
        } catch (e: AssertionError) {
            errors?.add(e)
            false
        }

    override fun toString(): String = "matcher()"
}

private class ValueMatcher(
    private val name: String,
    private val value: Any?,
    private val matches: (Any?) -> Boolean,
) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = matches(arg)

    override fun toString(): String = "$name(${value.toStr()})"
}

private class CaptureMatcher(val slot: Boolean, private val capture: (Any?) -> Unit) : Matcher<Any?>, CapturingMatcher {
    override fun match(arg: Any?): Boolean = true

    override fun capture(arg: Any?) {
        capture.invoke(arg)
    }

    override fun toString(): String = if (slot) "slotCapture()" else "capture()"
}

private class FilteredMatcher(val matcher: Matcher<Any?>, private val accepts: (Any?) -> Boolean) :
    Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
        accepts(arg) && matcher.match(arg, errors)

    override fun capture(arg: Any?) {
        (matcher as? CapturingMatcher)?.capture(arg)
    }

    override fun toString(): String = matcher.toString()
}

private class CompositeMatcher(
    private val name: String,
    private val operands: Array<Matcher<Any?>>,
    private val matches: (Any?, MutableList<AssertionError>?) -> Boolean,
) : Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = matches(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean = matches(arg, errors)

    override fun capture(arg: Any?) {
        operands.forEach { (it as? CapturingMatcher)?.capture(arg) }
    }

    override fun toString(): String = operands.joinToString(prefix = "$name(", postfix = ")")
}

private fun asserting(block: AssertScope.(Any?) -> Unit): (Any?) -> Boolean = { arg ->
    AssertScope(arg).block(arg)
    true
}

@Suppress("UNCHECKED_CAST")
private inline fun comparing(name: String, value: Comparable<*>, crossinline matches: (Int) -> Boolean): Matcher<Any?> =
    ValueMatcher(name, value) { it != null && matches((it as Comparable<Any?>).compareTo(value)) }

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

internal fun deepEquals(a: Any?, b: Any?): Boolean =
    a === b || when (a) {
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

internal fun Any?.toStr(): String =
    try {
        when (this) {
            null -> "null"
            is Array<*> -> contentDeepToString()
            is BooleanArray -> contentToString()
            is ByteArray -> contentToString()
            is CharArray -> contentToString()
            is ShortArray -> contentToString()
            is IntArray -> contentToString()
            is LongArray -> contentToString()
            is FloatArray -> contentToString()
            is DoubleArray -> contentToString()
            is KClass<*> -> simpleName ?: "<null name class>"
            is Function<*> -> "lambda {}"
            else -> toString()
        }
    } catch (e: Throwable) {
        "<error \"$e\">"
    }
