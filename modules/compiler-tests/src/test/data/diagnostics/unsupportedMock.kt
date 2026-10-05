// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: lib/Base.java
package lib;

public abstract class Base {
    abstract void hidden();
}

// FILE: test.kt
@file:Suppress("LOKKI_TYPE_ARGUMENT_NOT_CONCRETE", "LOKKI_TYPE_NOT_MOCKABLE")

package sample

import io.lokki.mock
import lib.Base

class Bolt

object Garage

abstract class Singleton private constructor()

sealed interface Shape

interface Box<T>

inline fun <reified T : Any> create(): T = <!LOKKI_UNSUPPORTED!>mock<T>()<!>

fun test() {
    <!LOKKI_UNSUPPORTED!>mock<Bolt>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Garage>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Singleton>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Base>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Shape>()<!>
    <!LOKKI_UNSUPPORTED!>mock<Box<String>>()<!>
    <!LOKKI_UNSUPPORTED!>mock<() -> Unit>()<!>
}
