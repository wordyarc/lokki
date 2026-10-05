// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: lib
// FILE: lib/Plugin.kt
package lib

abstract class Plugin {
    internal abstract fun load()

    abstract fun name(): String
}

abstract class Extension {
    internal open fun setup() {}
}

abstract class Singleton private constructor()

open class Factory internal constructor()

// MODULE: friend()(lib)
// FILE: lib/friend.kt
package lib

import io.lokki.mock

fun test() {
    mock<Plugin>()
    mock<Factory>()
}

// MODULE: main(lib)
// FILE: other/Base.java
package other;

public abstract class Base {
    abstract void hidden();

    public abstract void visible();
}

// FILE: other/Hidden.java
package other;

public abstract class Hidden {
    Hidden() {}

    public abstract void run();
}

// FILE: other/samePackage.kt
package other

import io.lokki.mock

fun test() {
    mock<Base>()
    mock<Hidden>()
}

// FILE: main.kt
package main

import io.lokki.mock
import lib.Extension
import lib.Factory
import lib.Plugin
import lib.Singleton
import other.Base
import other.Hidden

fun test() {
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Plugin<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Base<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Hidden<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Singleton<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Factory<!>>()
    mock<Extension>()
}
