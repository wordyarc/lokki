// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// MODULE: dep
// FILE: dep/Missing.kt
package dep

class Missing

open class MissingBase

// MODULE: lib(dep)
// FILE: lib/Lib.kt
package lib

import dep.Missing
import dep.MissingBase

abstract class Plugin {
    open fun optional(): Missing? = null

    abstract fun id(): Int
}

interface Store {
    var current: Missing?
}

abstract class Subclass : MissingBase()

// MODULE: main(lib)
// FILE: main.kt
@file:Suppress("LOKKI_TYPE_NOT_MOCKABLE")

package main

import io.lokki.mock
import lib.Plugin
import lib.Store
import lib.Subclass

fun test() {
    <!LOKKI_UNSUPPORTED!>mock<Plugin>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Store>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Subclass>()<!>
}
