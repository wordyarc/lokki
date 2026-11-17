package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal fun pluginNotApplied(): Nothing =
    throw LokkiException("lokki compiler plugin is not applied to this compilation")
