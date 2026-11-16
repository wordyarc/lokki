// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

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

import io.lokki.spy
import lib.Gauge
import java.util.ArrayList

interface Repo {
    fun find(id: Int): String
}

class RealRepo : Repo {
    override fun find(id: Int): String = "real"
}

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

abstract class Shape(val sides: Int = 3)

class Bolt

inline fun <reified T : Any> spyOf(value: T): T = spy(<!LOKKI_TYPE_ARGUMENT_NOT_CONCRETE!>value<!>)

fun test(repo: Repo, settings: Settings, pump: Pump, valve: Valve, gauge: Gauge) {
    spy<Settings>()
    spy<Pump>()
    spy<Shape>()
    spy<Repo>()
    spy<<!LOKKI_TYPE_NOT_MOCKABLE!>Bolt<!>>()

    spy(Engine(1))
    spy(Pump(pressure = 2))
    spy(ArrayList<String>())
    spy(Outer().Inner())
    spy<Repo>(RealRepo())
    spy(repo)
    spy(<!LOKKI_TYPE_NOT_MOCKABLE!>RealRepo()<!>)
    spy(<!LOKKI_TYPE_NOT_MOCKABLE!>Bolt()<!>)
}
