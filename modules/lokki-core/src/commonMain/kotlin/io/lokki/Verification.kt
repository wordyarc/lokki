package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Matchers
import io.lokki.internal.deepEquals
import io.lokki.internal.window
import io.lokki.internal.toStr
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun verify(
    ordering: Ordering = Ordering.UNORDERED,
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    timeout: Long = 0,
    verifyBlock: VerificationScope.() -> Any?,
): Unit = throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun coVerify(
    ordering: Ordering = Ordering.UNORDERED,
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    timeout: Long = 0,
    verifyBlock: suspend VerificationScope.() -> Any?,
): Unit = throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_ALL_BLOCK)
public fun verifyAll(inverse: Boolean = false, verifyBlock: VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_ORDER_BLOCK)
public fun verifyOrder(inverse: Boolean = false, verifyBlock: VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_SEQUENCE_BLOCK)
public fun verifySequence(inverse: Boolean = false, verifyBlock: VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_ALL_BLOCK)
public fun coVerifyAll(inverse: Boolean = false, verifyBlock: suspend VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_ORDER_BLOCK)
public fun coVerifyOrder(inverse: Boolean = false, verifyBlock: suspend VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_SEQUENCE_BLOCK)
public fun coVerifySequence(inverse: Boolean = false, verifyBlock: suspend VerificationScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun verifyCount(verifyBlock: CallCountVerificationScope.() -> Unit) {
    CallCountVerificationScope().verifyBlock()
}

public fun coVerifyCount(verifyBlock: CallCountCoVerificationScope.() -> Unit) {
    CallCountCoVerificationScope().verifyBlock()
}

@LokkiIntrinsic(LokkiIntrinsic.Kind.EXCLUDE_BLOCK)
public fun excludeRecords(current: Boolean = true, excludeBlock: MatcherScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.EXCLUDE_BLOCK)
public fun coExcludeRecords(current: Boolean = true, excludeBlock: suspend MatcherScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public abstract class VerificationScope internal constructor() : MatcherScope() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> withArg(noinline captureBlock: AssertScope.(T) -> Unit): T =
        push(Matchers.typed<T>(Matchers.withArg(captureBlock)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> withNullableArg(noinline captureBlock: AssertScope.(T?) -> Unit): T =
        push(Matchers.typed<T>(Matchers.withNullableArg(captureBlock)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> coWithArg(noinline captureBlock: suspend AssertScope.(T) -> Unit): T =
        push(Matchers.typed<T>(Matchers.coWithArg(captureBlock)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> coWithNullableArg(noinline captureBlock: suspend AssertScope.(T?) -> Unit): T =
        push(Matchers.typed<T>(Matchers.coWithNullableArg(captureBlock)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.WAS_NOT_CALLED)
    public infix fun Any.wasNot(called: Called): Unit =
        throw LokkiException("'wasNot Called' must be a statement of a verify block")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.WAS_NOT_CALLED)
    public infix fun List<Any>.wasNot(called: Called): Unit =
        throw LokkiException("'wasNot Called' must be a statement of a verify block")
}

@LokkiDslMarker
public class AssertScope internal constructor(public val actual: Any?) {
    public fun checkEquals(expected: Any?) {
        if (!deepEquals(expected, actual)) throw AssertionError(difference(expected))
    }

    public fun checkEquals(msg: String, expected: Any?) {
        if (!deepEquals(expected, actual)) throw AssertionError("$msg ${difference(expected)}")
    }

    private fun difference(expected: Any?): String = "expected [${expected.toStr()}] but found [${actual.toStr()}]"
}

public object Called

public enum class Ordering {
    UNORDERED,
    ALL,
    ORDERED,
    SEQUENCE,
}

@LokkiDslMarker
public class CallCountVerificationScope internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun Int.times(verifyBlock: VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun IntRange.times(verifyBlock: VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")
}

@LokkiDslMarker
public class CallCountCoVerificationScope internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun Int.times(verifyBlock: suspend VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun IntRange.times(verifyBlock: suspend VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")
}

@LokkiIntrinsic(LokkiIntrinsic.Kind.CONFIRM_VERIFIED_STATIC)
public fun confirmVerified(function: KFunction<*>, vararg functions: KFunction<*>, clear: Boolean = false): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@LokkiIntrinsic(LokkiIntrinsic.Kind.CONFIRM_VERIFIED_STATIC)
public fun confirmVerified(property: KProperty<*>, vararg properties: KProperty<*>, clear: Boolean = false): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun confirmVerified(vararg mocks: Any, clear: Boolean = false) {
    val window = window("confirmVerified", mocks)
    if (mocks.isNotEmpty()) window.states.forEach { it.checkNotShared() }
    window.states.forEach { it.confirmVerified(window.since) }
    if (!clear) return
    for (state in window.states) {
        state.clear(
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = true,
            exclusionRules = false,
            since = window.since,
        )
    }
}
