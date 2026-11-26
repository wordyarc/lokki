package io.lokki.internal

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException

@InternalLokkiApi
public fun pluginNotApplied(): Nothing = pluginNotApplied("this compilation")

internal fun pluginNotApplied(compilation: String): Nothing =
    throw LokkiException(
        "lokki compiler plugin is not applied to $compilation: with Gradle, name the compilation in " +
            "lokki { testCompilations(...) }, or set applyToMain = true for main code"
    )
