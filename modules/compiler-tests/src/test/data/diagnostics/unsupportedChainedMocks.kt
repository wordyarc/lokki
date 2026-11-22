// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:Suppress("LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION")

package sample

import io.lokki.every

interface Door {
    fun open(): Boolean
}

interface Car {
    fun door(index: Int): Door
}

fun pick(index: Int): Car = TODO()

fun test(car: Car) {
    every { <!LOKKI_UNSUPPORTED!>pick(any())<!>.door(1).open() } returns true

    every { car.door(any()).open() } returns true
}
