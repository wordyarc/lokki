// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress("LOKKI_TYPE_ARGUMENT_NOT_CONCRETE", "LOKKI_TYPE_NOT_MOCKABLE")

package sample

import io.lokki.spy

class Bolt

open class Engine(val power: Int)

inline fun <reified T : Any> spyOf(value: T): T = <!LOKKI_UNSUPPORTED!>spy(value)<!>

fun label(): String = "late"

fun reordered() {
    <!LOKKI_UNSUPPORTED!>spy(Engine(1), block = {}, name = label())<!>
}

fun test() {
    <!LOKKI_UNSUPPORTED!>spy<Bolt>()<!>
    <!LOKKI_UNSUPPORTED!>spy(Bolt())<!>
}
