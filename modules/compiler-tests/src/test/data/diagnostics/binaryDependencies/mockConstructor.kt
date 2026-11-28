// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// MODULE: main
// INTERCEPTABLE: sample.Open
// FILE: main.kt
package sample

class Open

class Closed

abstract class Base

interface Shape

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mockConstructor
import kotlin.reflect.KClass
import sample.Base
import sample.Closed
import sample.Open
import sample.Shape

fun check(type: KClass<*>) {
    mockConstructor(Open::class)
    mockConstructor(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Closed::class<!>)
    mockConstructor(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Base::class<!>)
    mockConstructor(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Shape::class<!>)
    mockConstructor(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>type<!>)
}
