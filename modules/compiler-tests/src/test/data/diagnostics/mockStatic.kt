// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
package test

import io.lokki.clearStaticMock
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.mockStatic
import io.lokki.unmockStatic
import kotlin.reflect.KFunction

fun local(): Int = 1

val Int.doubled: Int
    get() = this * 2

object Clock {
    fun now(): Long = 1
}

open class Service {
    fun f(): Int = 1
}

fun test(functions: Array<KFunction<*>>, function: KFunction<*>) {
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::local<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Int::doubled<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Clock::now<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Service::f<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>System::currentTimeMillis<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>String::isBlank<!>) {}
    mockStatic(<!LOKKI_REFERENCE_NOT_LITERAL!>function<!>)
    mockStatic(<!LOKKI_REFERENCE_NOT_LITERAL!>*functions<!>)
    mockStatic(<!LOKKI_REFERENCE_NOT_LITERAL!>"a"::plus<!>)
    unmockStatic(<!LOKKI_REFERENCE_NOT_LITERAL!>function<!>)
    clearStaticMock(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::local<!>)
    confirmVerified(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::local<!>, <!LOKKI_REFERENCE_NOT_LITERAL!>function<!>, clear = true)
    every { <!LOKKI_NOT_INTERCEPTABLE!>local<!>() } returns 2
}
