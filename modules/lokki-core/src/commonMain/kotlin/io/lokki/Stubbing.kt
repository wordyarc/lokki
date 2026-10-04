package io.lokki

import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.STUB_BLOCK)
public fun <T> every(stubBlock: MatcherScope.() -> T): StubScope<T> =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

@SubclassOptInRequired(InternalLokkiApi::class)
public interface StubScope<T> {
    public infix fun returns(returnValue: T)
}
