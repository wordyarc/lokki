package io.lokki

import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.VERIFY_BLOCK)
public fun verify(
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    verifyBlock: VerificationScope.() -> Any?,
): Unit = throw LokkiException("lokki compiler plugin is not applied to this compilation")

public abstract class VerificationScope internal constructor() : MatcherScope()
