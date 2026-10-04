package io.lokki

import io.lokki.internal.LokkiIntrinsic

public abstract class MatcherScope internal constructor() {
    @LokkiIntrinsic(LokkiIntrinsic.Kind.MATCHER)
    public inline fun <reified T> any(): T =
        throw LokkiException("lokki compiler plugin is not applied to this compilation")
}
