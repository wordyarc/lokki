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
    public inline fun <reified T : Any> coMatch(noinline matcher: suspend (T) -> Boolean): T =
        push(Matchers.typed<T>(Matchers.coMatch(matcher)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> coMatchNullable(noinline matcher: suspend (T?) -> Boolean): T =
        push(Matchers.typed<T>(Matchers.coMatchNullable(matcher)), T::class)

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

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> anyVararg(): Array<T> = pushVararg(Matchers.anyVararg<T>(), emptyArray())

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyBooleanVararg(): BooleanArray = pushVararg(Matchers.anyBooleanVararg(), BooleanArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyByteVararg(): ByteArray = pushVararg(Matchers.anyByteVararg(), ByteArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyCharVararg(): CharArray = pushVararg(Matchers.anyCharVararg(), CharArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyShortVararg(): ShortArray = pushVararg(Matchers.anyShortVararg(), ShortArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyIntVararg(): IntArray = pushVararg(Matchers.anyIntVararg(), IntArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyLongVararg(): LongArray = pushVararg(Matchers.anyLongVararg(), LongArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyFloatVararg(): FloatArray = pushVararg(Matchers.anyFloatVararg(), FloatArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun anyDoubleVararg(): DoubleArray = pushVararg(Matchers.anyDoubleVararg(), DoubleArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> varargAll(noinline matcher: VarargScope.(T) -> Boolean): Array<T> =
        pushVararg(Matchers.varargAll(matcher), emptyArray())

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> varargAllNullable(noinline matcher: VarargScope.(T?) -> Boolean): Array<T> =
        pushVararg(Matchers.varargAllNullable(matcher), emptyArray())

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllBoolean(matcher: VarargScope.(Boolean) -> Boolean): BooleanArray =
        pushVararg(Matchers.varargAllBoolean(matcher), BooleanArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllByte(matcher: VarargScope.(Byte) -> Boolean): ByteArray =
        pushVararg(Matchers.varargAllByte(matcher), ByteArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllChar(matcher: VarargScope.(Char) -> Boolean): CharArray =
        pushVararg(Matchers.varargAllChar(matcher), CharArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllShort(matcher: VarargScope.(Short) -> Boolean): ShortArray =
        pushVararg(Matchers.varargAllShort(matcher), ShortArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllInt(matcher: VarargScope.(Int) -> Boolean): IntArray =
        pushVararg(Matchers.varargAllInt(matcher), IntArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllLong(matcher: VarargScope.(Long) -> Boolean): LongArray =
        pushVararg(Matchers.varargAllLong(matcher), LongArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllFloat(matcher: VarargScope.(Float) -> Boolean): FloatArray =
        pushVararg(Matchers.varargAllFloat(matcher), FloatArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAllDouble(matcher: VarargScope.(Double) -> Boolean): DoubleArray =
        pushVararg(Matchers.varargAllDouble(matcher), DoubleArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> varargAny(noinline matcher: VarargScope.(T) -> Boolean): Array<T> =
        pushVararg(Matchers.varargAny(matcher), emptyArray())

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> varargAnyNullable(noinline matcher: VarargScope.(T?) -> Boolean): Array<T> =
        pushVararg(Matchers.varargAnyNullable(matcher), emptyArray())

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyBoolean(matcher: VarargScope.(Boolean) -> Boolean): BooleanArray =
        pushVararg(Matchers.varargAnyBoolean(matcher), BooleanArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyByte(matcher: VarargScope.(Byte) -> Boolean): ByteArray =
        pushVararg(Matchers.varargAnyByte(matcher), ByteArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyChar(matcher: VarargScope.(Char) -> Boolean): CharArray =
        pushVararg(Matchers.varargAnyChar(matcher), CharArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyShort(matcher: VarargScope.(Short) -> Boolean): ShortArray =
        pushVararg(Matchers.varargAnyShort(matcher), ShortArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyInt(matcher: VarargScope.(Int) -> Boolean): IntArray =
        pushVararg(Matchers.varargAnyInt(matcher), IntArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyLong(matcher: VarargScope.(Long) -> Boolean): LongArray =
        pushVararg(Matchers.varargAnyLong(matcher), LongArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyFloat(matcher: VarargScope.(Float) -> Boolean): FloatArray =
        pushVararg(Matchers.varargAnyFloat(matcher), FloatArray(0))

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun varargAnyDouble(matcher: VarargScope.(Double) -> Boolean): DoubleArray =
        pushVararg(Matchers.varargAnyDouble(matcher), DoubleArray(0))

    @PublishedApi
    internal fun <T> push(matcher: Matcher<Any?>, type: KClass<*>): T {
        frame += matcher
        @Suppress("UNCHECKED_CAST")
        return placeholder(type) as T
    }

    @PublishedApi
    internal fun <A> pushVararg(matcher: Matcher<Any?>, placeholder: A): A {
        frame += matcher
        return placeholder
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

@LokkiDslMarker
public class VarargScope internal constructor(public val position: Int, public val nArgs: Int)

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
