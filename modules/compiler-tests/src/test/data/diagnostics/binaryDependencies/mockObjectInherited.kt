// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// MODULE: main
// MOCKABLE: sample.Service
// FILE: main.kt
package sample

open class Base {
    open fun greet(): String = "hello"

    fun sealedByFinal(): String = "final"
}

object Service : Base()

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import sample.Service

fun check() {
    every { Service.greet() } returns "x"
    every { Service.<!LOKKI_NOT_INTERCEPTABLE!>sealedByFinal<!>() } returns "x"
}
