package io.lokki

import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.states

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun verify(
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    verifyBlock: VerificationScope.() -> Any?,
): Unit = throw LokkiException("lokki compiler plugin is not applied to this compilation")

public abstract class VerificationScope internal constructor() : MatcherScope()

public fun confirmVerified(vararg mocks: Any, clear: Boolean = false) {
    val states = states("confirmVerified", mocks)
    states.forEach { it.confirmVerified() }
    if (!clear) return
    for (state in states) {
        state.clear(answers = false, recordedCalls = true, verificationMarks = true, exclusionRules = false)
    }
}
