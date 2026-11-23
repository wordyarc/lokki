package io.lokki.internal

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException

@InternalLokkiApi
public fun pluginNotApplied(): Nothing =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")
