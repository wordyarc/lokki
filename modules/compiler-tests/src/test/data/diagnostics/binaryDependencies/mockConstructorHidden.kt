// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// INTERCEPTABLE: sample.**
// FILE: main.kt
package sample

class Hidden private constructor()

class Factory internal constructor()

class Visible

// MODULE: test(main)
// FILE: test.kt
package test

import io.lokki.mock
import io.lokki.mockConstructor
import sample.Factory
import sample.Hidden
import sample.Visible

fun test() {
    mock<Hidden>()
    mock<Factory>()
    mockConstructor(Visible::class)
    <!LOKKI_UNSUPPORTED!>mockConstructor(Factory::class)<!>
}
