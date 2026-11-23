package io.lokki.bdd

import io.lokki.MatcherScope
import io.lokki.Ordering
import io.lokki.StubScope
import io.lokki.VerificationScope
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.pluginNotApplied

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> given(stubBlock: MatcherScope.() -> T): StubScope<T, T> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> coGiven(stubBlock: suspend MatcherScope.() -> T): StubScope<T, T> =
    pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun then(
    ordering: Ordering = Ordering.UNORDERED,
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    timeout: Long = 0,
    verifyBlock: VerificationScope.() -> Any?,
): Unit = pluginNotApplied()

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun coThen(
    ordering: Ordering = Ordering.UNORDERED,
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    timeout: Long = 0,
    verifyBlock: suspend VerificationScope.() -> Any?,
): Unit = pluginNotApplied()
