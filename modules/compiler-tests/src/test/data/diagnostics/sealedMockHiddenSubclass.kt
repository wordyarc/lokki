// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// FILE: shapes.kt
package shapes

sealed interface Hidden

private class Secret : Hidden

open class Closed private constructor() : Hidden

// MODULE: test(main)
// FILE: test.kt
package test

import io.lokki.mock
import shapes.Hidden

fun test() {
    <!LOKKI_TYPE_NOT_MOCKABLE!>mock<Hidden>()<!>
}
