// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
@file:Suppress("LOKKI_OBJECT_NOT_INTERCEPTABLE", "LOKKI_REFERENCE_NOT_LITERAL", "LOKKI_NOT_INTERCEPTABLE")

package test

import io.lokki.every
import io.lokki.mockStatic
import io.lokki.unmockStatic
import kotlin.reflect.KFunction

fun local(): Int = 1

fun test(functions: Array<KFunction<*>>, function: KFunction<*>) {
    mockStatic(<!LOKKI_UNSUPPORTED!>::local<!>)
    <!LOKKI_UNSUPPORTED!>mockStatic(*functions)<!>
    mockStatic(<!LOKKI_UNSUPPORTED!>function<!>)
    unmockStatic(<!LOKKI_UNSUPPORTED!>::local<!>)
    every { <!LOKKI_UNSUPPORTED!>local()<!> } returns 2
}
