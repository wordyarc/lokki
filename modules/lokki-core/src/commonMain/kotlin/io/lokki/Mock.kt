package io.lokki

import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
public inline fun <reified T : Any> mock(): T =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")
