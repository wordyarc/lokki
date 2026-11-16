// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: lib/Gauge.java
package lib;

public class Gauge {
    public int read() {
        return 1;
    }

    protected void calibrate() {
    }
}

// FILE: test.kt
package sample

import io.lokki.Spy
import io.lokki.spy
import lib.Gauge

open class Settings {
    open fun size(): Int = 1
}

open class Engine(val power: Int) {
    open fun start(): Boolean = true
}

open class Pump(val pressure: Int = 1) {
    fun prime(): Boolean = true

    protected open fun vent() {}
}

open class Valve {
    val size: Int = 1

    internal open fun close() {}
}

class Outer {
    open inner class Inner
}

fun test(settings: Settings, pump: Pump, valve: Valve, gauge: Gauge) {
    <!LOKKI_TYPE_NOT_MOCKABLE!>spy<Engine>()<!>
    <!LOKKI_TYPE_NOT_MOCKABLE!>spy<Outer.Inner>()<!>

    spy(<!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>settings<!>)
    spy(Settings().<!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>apply { size() }<!>)
    spy(<!LOKKI_UNSAFE_SPY_DELEGATION!>pump<!>)
    spy(<!LOKKI_UNSAFE_SPY_DELEGATION!>valve<!>)
    spy(<!LOKKI_UNSAFE_SPY_DELEGATION!>gauge<!>)
    spy(name = "named", obj = <!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>settings<!>)

    spy<Settings>()
    spy(Engine(1))
    spy(Outer().Inner())
}

class Fixture {
    <!LOKKI_TYPE_NOT_MOCKABLE!>@Spy
    lateinit var lateEngine: Engine<!>

    @Spy
    var existing: Settings = <!LOKKI_SPY_ARGUMENT_NOT_CONSTRUCTOR_CALL!>shared<!>

    @Spy
    var pump: Pump = <!LOKKI_UNSAFE_SPY_DELEGATION!>sharedPump<!>

    @Spy
    lateinit var lateSettings: Settings

    companion object {
        val shared = Settings()
        val sharedPump = Pump()
    }
}
