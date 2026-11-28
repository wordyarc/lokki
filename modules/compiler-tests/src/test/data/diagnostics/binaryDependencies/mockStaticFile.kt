// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
// MODULE: main
// INTERCEPTABLE: sample.Picked
// FILE: picked.kt
package sample

fun plain(): String = "plain"

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.mockStatic

fun check() {
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>"sample.PickedKt"<!>)
}
