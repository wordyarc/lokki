// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: lib/Gauge.java
package lib;

public class Gauge {
    protected void calibrate() {
    }
}

// FILE: test.kt
@file:Suppress("LOKKI_TYPE_ARGUMENT_NOT_CONCRETE", "LOKKI_TYPE_NOT_MOCKABLE", "LOKKI_UNSAFE_SPY_DELEGATION")

package sample

import io.lokki.spy
import lib.Gauge

class Bolt

open class Engine(val power: Int)

open class Pump {
    fun prime(): Boolean = true

    protected open fun vent() {}
}

class Outer {
    open inner class Inner
}

inline fun <reified T : Any> spyOf(value: T): T = <!LOKKI_UNSUPPORTED!>spy(value)<!>

fun label(): String = "late"

fun reordered() {
    <!LOKKI_UNSUPPORTED!>spy(Engine(1), block = {}, name = label())<!>
}

fun test(pump: Pump, gauge: Gauge) {
    <!LOKKI_UNSUPPORTED!>spy<Bolt>()<!>
    <!LOKKI_UNSUPPORTED!>spy<Engine>()<!>
    <!LOKKI_UNSUPPORTED!>spy<Outer.Inner>()<!>
    <!LOKKI_UNSUPPORTED!>spy(Bolt())<!>
    <!LOKKI_UNSUPPORTED!>spy(pump)<!>
    <!LOKKI_UNSUPPORTED!>spy(gauge)<!>
}
