package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.EqMatcher
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Matchers
import kotlin.reflect.KClass

@LokkiDslMarker
public abstract class MatcherScope internal constructor() {
    private val frame = ArrayList<Matcher<Any?>>()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> any(): T = push(Matchers.typed<T>(AnyMatcher), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> anyNullable(): T = push(Matchers.typed<T>(AnyMatcher), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> allAny(): T = push(AnyMatcher, T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> eq(value: T, inverse: Boolean = false): T =
        push(Matchers.eq(value, inverse), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> neq(value: T): T = push(Matchers.neq(value), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> refEq(value: T, inverse: Boolean = false): T =
        push(Matchers.refEq(value, inverse), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> nrefEq(value: T): T = push(Matchers.nrefEq(value), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> cmpEq(value: T): T =
        push(Matchers.typed<T>(Matchers.cmpEq(value)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> more(value: T, andEquals: Boolean = false): T =
        push(Matchers.typed<T>(Matchers.more(value, andEquals)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> less(value: T, andEquals: Boolean = false): T =
        push(Matchers.typed<T>(Matchers.less(value, andEquals)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Comparable<T>> range(
        from: T,
        to: T,
        fromInclusive: Boolean = true,
        toInclusive: Boolean = true,
    ): T = push(Matchers.typed<T>(Matchers.range(from, to, fromInclusive, toInclusive)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> and(left: T, right: T): T {
        val second = pop()
        return push(Matchers.and(pop(), second), T::class)
    }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> or(left: T, right: T): T {
        val second = pop()
        return push(Matchers.or(pop(), second), T::class)
    }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> not(value: T): T = push(Matchers.not(pop()), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> isNull(inverse: Boolean = false): T = push(Matchers.isNull(inverse), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> ofType(): T = push(Matchers.ofType<T>(), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> match(matcher: Matcher<T>): T = push(Matchers.match(matcher), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> match(noinline matcher: (T) -> Boolean): T =
        push(Matchers.typed<T>(Matchers.match(matcher)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> matchNullable(noinline matcher: (T?) -> Boolean): T =
        push(Matchers.typed<T>(Matchers.matchNullable(matcher)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> capture(lst: MutableList<T>): T =
        push(Matchers.typed<T>(Matchers.capture(lst)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> capture(lst: CapturingSlot<T>): T =
        push(Matchers.typed<T>(Matchers.capture(lst)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> captureNullable(lst: MutableList<T?>): T? =
        push(Matchers.typed<T>(Matchers.captureNullable(lst)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> captureNullable(lst: CapturingSlot<T?>): T? =
        push(Matchers.typed<T>(Matchers.captureNullable(lst)), T::class)

    @PublishedApi
    internal fun <T> push(matcher: Matcher<Any?>, type: KClass<*>): T {
        frame += matcher
        @Suppress("UNCHECKED_CAST")
        return placeholder(type) as T
    }

    @PublishedApi
    internal fun pop(): Matcher<Any?> =
        frame.removeLastOrNull()
            ?: throw LokkiException("an operand of and, or, not in a matcher helper is not a matcher")

    @PublishedApi
    internal fun matcherOf(value: Any?): Matcher<Any?> {
        val matcher = when (frame.size) {
            0 -> EqMatcher(value)
            1 -> frame[0]
            else -> throw LokkiException("matcher helper produced ${frame.size} matchers")
        }
        frame.clear()
        return matcher
    }
}

private fun placeholder(type: KClass<*>): Any? =
    when (type) {
        Boolean::class -> false
        Byte::class -> 0.toByte()
        Char::class -> '\u0000'
        Short::class -> 0.toShort()
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0f
        Double::class -> 0.0
        else -> null
    }
