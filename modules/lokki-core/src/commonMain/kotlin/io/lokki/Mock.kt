package io.lokki

import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK)
public inline fun <reified T : Any> mock(name: String? = null, block: T.() -> Unit = {}): T =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")

public fun isMock(obj: Any): Boolean = obj is GeneratedMock
