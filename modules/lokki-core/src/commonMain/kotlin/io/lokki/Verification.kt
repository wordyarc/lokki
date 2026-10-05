package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.Matchers
import io.lokki.internal.deepEquals
import io.lokki.internal.states
import io.lokki.internal.toStr

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun verify(
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    verifyBlock: VerificationScope.() -> Any?,
): Unit = throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun verifyCount(verifyBlock: CallCountVerificationScope.() -> Unit) {
    CallCountVerificationScope().verifyBlock()
}

@LokkiIntrinsic(LokkiIntrinsic.Kind.EXCLUDE_BLOCK)
public fun excludeRecords(current: Boolean = true, excludeBlock: MatcherScope.() -> Any?): Unit =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public abstract class VerificationScope internal constructor() : MatcherScope() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> withArg(noinline captureBlock: AssertScope.(T) -> Unit): T =
        push(Matchers.typed<T>(Matchers.withArg(captureBlock)), T::class)

    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T : Any> withNullableArg(noinline captureBlock: AssertScope.(T?) -> Unit): T =
        push(Matchers.typed<T>(Matchers.withNullableArg(captureBlock)), T::class)

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

@LokkiDslMarker
public class CallCountVerificationScope internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun Int.times(verifyBlock: VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")

    @LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
    public operator fun IntRange.times(verifyBlock: VerificationScope.() -> Any?): Unit =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")
}

public fun confirmVerified(vararg mocks: Any, clear: Boolean = false) {
    val states = states("confirmVerified", mocks)
    states.forEach { it.confirmVerified() }
    if (!clear) return
    for (state in states) {
        state.clear(answers = false, recordedCalls = true, verificationMarks = true, exclusionRules = false)
    }
}
