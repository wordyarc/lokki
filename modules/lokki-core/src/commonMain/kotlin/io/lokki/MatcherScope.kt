package io.lokki

import io.lokki.internal.AnyMatcher
import io.lokki.internal.EqMatcher
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Matchers
import io.lokki.internal.pluginNotApplied
import kotlin.reflect.KClass

@LokkiDslMarker
public abstract class MatcherScope internal constructor() {
    private val frame = ArrayList<Matcher<Any?>>()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> any(): T = push(Matchers.typed<T>(AnyMatcher), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun <T : Any> any(classifier: KClass<T>): T = push(Matchers.any(classifier), classifier)

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
    public inline fun <reified T : Any, R : T> ofType(cls: KClass<R>): T = push(Matchers.ofType(cls), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> match(matcher: Matcher<T>): T = push(Matchers.match(matcher), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public fun <T : Any> match(matcher: Matcher<T>, kclass: KClass<T>): T =
        push(Matchers.match(matcher, kclass), kclass)

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
    public inline fun <reified T : Function<*>> captureLambda(): T =
        push(Matchers.typed<T>(Matchers.captureLambda()), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> captureCoroutine(): T =
        push(Matchers.typed<T>(Matchers.captureCoroutine()), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> array(vararg elements: Any?): T =
        push(Matchers.typed<T>(Matchers.array(pop(elements.size))), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> varargArray(noinline predicate: (Array<out T>) -> Boolean): Array<T> =
        pushVararg(Matchers.varargArray(predicate), emptyArray())

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

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <R> invoke(): () -> R =
        pushInvoke<() -> R>("invoke") { it() }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, R> invoke(arg1: A1): (A1) -> R =
        pushInvoke<(A1) -> R>("invoke", arg1) { it(arg1) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, R> invoke(arg1: A1, arg2: A2): (A1, A2) -> R =
        pushInvoke<(A1, A2) -> R>("invoke", arg1, arg2) { it(arg1, arg2) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, R> invoke(arg1: A1, arg2: A2, arg3: A3): (A1, A2, A3) -> R =
        pushInvoke<(A1, A2, A3) -> R>("invoke", arg1, arg2, arg3) { it(arg1, arg2, arg3) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, R> invoke(arg1: A1, arg2: A2, arg3: A3, arg4: A4): (A1, A2, A3, A4) -> R =
        pushInvoke<(A1, A2, A3, A4) -> R>("invoke", arg1, arg2, arg3, arg4) { it(arg1, arg2, arg3, arg4) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
    ): (A1, A2, A3, A4, A5) -> R =
        pushInvoke<(A1, A2, A3, A4, A5) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5,
        ) {
            it(arg1, arg2, arg3, arg4, arg5)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
    ): (A1, A2, A3, A4, A5, A6) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
    ): (A1, A2, A3, A4, A5, A6, A7) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
    ): (A1, A2, A3, A4, A5, A6, A7, A8) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R> invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
    > invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R>(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
    > invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R =
        pushInvoke<
            (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
            ) -> R,
        >(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20, arg21,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
    > invoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21, arg22: A22,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R =
        pushInvoke<
            (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
            ) -> R,
        >(
            "invoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20, arg21, arg22,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21, arg22,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <R> coInvoke(): suspend () -> R =
        pushCoInvoke<suspend () -> R>("coInvoke") { it() }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, R> coInvoke(arg1: A1): suspend (A1) -> R =
        pushCoInvoke<suspend (A1) -> R>("coInvoke", arg1) { it(arg1) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, R> coInvoke(arg1: A1, arg2: A2): suspend (A1, A2) -> R =
        pushCoInvoke<suspend (A1, A2) -> R>("coInvoke", arg1, arg2) { it(arg1, arg2) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, R> coInvoke(arg1: A1, arg2: A2, arg3: A3): suspend (A1, A2, A3) -> R =
        pushCoInvoke<suspend (A1, A2, A3) -> R>("coInvoke", arg1, arg2, arg3) { it(arg1, arg2, arg3) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4,
    ): suspend (A1, A2, A3, A4) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4) -> R>("coInvoke", arg1, arg2, arg3, arg4) { it(arg1, arg2, arg3, arg4) }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
    ): suspend (A1, A2, A3, A4, A5) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5,
        ) {
            it(arg1, arg2, arg3, arg4, arg5)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
    ): suspend (A1, A2, A3, A4, A5, A6) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
    ): suspend (A1, A2, A3, A4, A5, A6, A7) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R> coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R>(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R,
    > coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19,
            ) -> R,
        >(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
    > coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20,
            ) -> R,
        >(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
    > coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
            ) -> R,
        >(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20, arg21,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
    > coInvoke(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21, arg22: A22,
    ): suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
    ) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
            ) -> R,
        >(
            "coInvoke", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15,
            arg16, arg17, arg18, arg19, arg20, arg21, arg22,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21, arg22,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <R> invokeWith(): () -> R =
        pushInvoke<() -> R>("invokeWith") { it() }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, R> invokeWith(arg1: A1): (A1) -> R =
        pushInvoke<(A1) -> R>("invokeWith", arg1) { it(arg1) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, R> invokeWith(arg1: A1, arg2: A2): (A1, A2) -> R =
        pushInvoke<(A1, A2) -> R>("invokeWith", arg1, arg2) { it(arg1, arg2) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, R> invokeWith(arg1: A1, arg2: A2, arg3: A3): (A1, A2, A3) -> R =
        pushInvoke<(A1, A2, A3) -> R>("invokeWith", arg1, arg2, arg3) { it(arg1, arg2, arg3) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, R> invokeWith(arg1: A1, arg2: A2, arg3: A3, arg4: A4): (A1, A2, A3, A4) -> R =
        pushInvoke<(A1, A2, A3, A4) -> R>("invokeWith", arg1, arg2, arg3, arg4) { it(arg1, arg2, arg3, arg4) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
    ): (A1, A2, A3, A4, A5) -> R =
        pushInvoke<(A1, A2, A3, A4, A5) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5,
        ) {
            it(arg1, arg2, arg3, arg4, arg5)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
    ): (A1, A2, A3, A4, A5, A6) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
    ): (A1, A2, A3, A4, A5, A6, A7) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
    ): (A1, A2, A3, A4, A5, A6, A7, A8) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R> invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R,
    > invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
    > invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R =
        pushInvoke<(A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R>(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
    > invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R =
        pushInvoke<
            (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
            ) -> R,
        >(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20, arg21,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
    > invokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21, arg22: A22,
    ): (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22) -> R =
        pushInvoke<
            (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
            ) -> R,
        >(
            "invokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21, arg22,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <R> coInvokeWith(): suspend () -> R =
        pushCoInvoke<suspend () -> R>("coInvokeWith") { it() }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, R> coInvokeWith(arg1: A1): suspend (A1) -> R =
        pushCoInvoke<suspend (A1) -> R>("coInvokeWith", arg1) { it(arg1) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, R> coInvokeWith(arg1: A1, arg2: A2): suspend (A1, A2) -> R =
        pushCoInvoke<suspend (A1, A2) -> R>("coInvokeWith", arg1, arg2) { it(arg1, arg2) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, R> coInvokeWith(arg1: A1, arg2: A2, arg3: A3): suspend (A1, A2, A3) -> R =
        pushCoInvoke<suspend (A1, A2, A3) -> R>("coInvokeWith", arg1, arg2, arg3) { it(arg1, arg2, arg3) }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4,
    ): suspend (A1, A2, A3, A4) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4,
        ) {
            it(arg1, arg2, arg3, arg4)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5,
    ): suspend (A1, A2, A3, A4, A5) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5,
        ) {
            it(arg1, arg2, arg3, arg4, arg5)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6,
    ): suspend (A1, A2, A3, A4, A5, A6) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7,
    ): suspend (A1, A2, A3, A4, A5, A6, A7) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16,
        ) {
            it(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16)
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, R> coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R =
        pushCoInvoke<suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18) -> R>(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, R,
    > coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19,
            ) -> R,
        >(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, R,
    > coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20,
            ) -> R,
        >(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, R,
    > coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21,
    ): suspend (A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21,
            ) -> R,
        >(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20, arg21,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21,
            )
        }

    @ExperimentalLokkiApi
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22, R,
    > coInvokeWith(
        arg1: A1, arg2: A2, arg3: A3, arg4: A4, arg5: A5, arg6: A6, arg7: A7, arg8: A8, arg9: A9, arg10: A10,
        arg11: A11, arg12: A12, arg13: A13, arg14: A14, arg15: A15, arg16: A16, arg17: A17, arg18: A18, arg19: A19,
        arg20: A20, arg21: A21, arg22: A22,
    ): suspend (
        A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
    ) -> R =
        pushCoInvoke<
            suspend (
                A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15, A16, A17, A18, A19, A20, A21, A22,
            ) -> R,
        >(
            "coInvokeWith", arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14,
            arg15, arg16, arg17, arg18, arg19, arg20, arg21, arg22,
        ) {
            it(
                arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16,
                arg17, arg18, arg19, arg20, arg21, arg22,
            )
        }

    @LokkiIntrinsic(LokkiIntrinsic.Kind.CONSTRUCTED)
    public inline fun <reified T : Any> anyConstructed(): T = pluginNotApplied()

    @LokkiIntrinsic(LokkiIntrinsic.Kind.CONSTRUCTED)
    public inline fun <reified T : Any> constructedWith(vararg matchers: Any?): T = pluginNotApplied()

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
    internal inline fun <reified T> pushInvoke(
        name: String,
        vararg arguments: Any?,
        noinline invocation: (T) -> Unit,
    ): T = push(Matchers.typed<T>(Matchers.invoke(name, arguments, invocation)), T::class)

    @PublishedApi
    internal inline fun <reified T> pushCoInvoke(
        name: String,
        vararg arguments: Any?,
        noinline invocation: suspend (T) -> Unit,
    ): T = push(Matchers.typed<T>(Matchers.coInvoke(name, arguments, invocation)), T::class)

    @PublishedApi
    internal fun pop(): Matcher<Any?> =
        frame.removeLastOrNull()
            ?: throw LokkiException("an operand of and, or, not in a matcher helper is not a matcher")

    @PublishedApi
    internal fun pop(count: Int): Array<Matcher<Any?>> {
        if (frame.size < count) throw LokkiException("an element of array in a matcher helper is not a matcher")
        val elements = frame.subList(frame.size - count, frame.size)
        return elements.toTypedArray().also { elements.clear() }
    }

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
