// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
@file:Suppress("LOKKI_OBJECT_NOT_INTERCEPTABLE")

package test

import io.lokki.mockObject

object Local {
    fun f(): Int = 1
}

fun test(objects: Array<Any>) {
    mockObject(<!LOKKI_UNSUPPORTED!>Local<!>)
    <!LOKKI_UNSUPPORTED!>mockObject(*objects)<!>
}
