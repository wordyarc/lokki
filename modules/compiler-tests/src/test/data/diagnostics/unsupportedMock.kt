// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.mock

open class Engine

sealed interface Shape

interface Box<T>

inline fun <reified T : Any> create(): T = <!LOKKI_UNSUPPORTED!>mock<T>()<!>

fun test() {
    <!LOKKI_UNSUPPORTED!>mock<Engine>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Shape>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Box<String>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<() -> Unit>()<!>
}
