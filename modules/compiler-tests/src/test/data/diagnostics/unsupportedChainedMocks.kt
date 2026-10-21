// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:ChainedMocks
@file:Suppress("LOKKI_TYPE_NOT_MOCKABLE", "LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION")

package sample

import io.lokki.ChainedMocks
import io.lokki.every
import io.lokki.verify

interface Door {
    fun open(): Boolean
}

open class Locked private constructor() {
    open fun open(): Boolean = false
}

interface Car {
    fun door(index: Int): Door
    fun locked(index: Int): Locked
}

fun pick(index: Int): Car = TODO()

fun test(car: Car) {
    every { car.<!LOKKI_UNSUPPORTED!>locked(any())<!>.open() } returns true
    verify { car.<!LOKKI_UNSUPPORTED!>locked(any())<!>.open() }
    every { <!LOKKI_UNSUPPORTED!>pick(any())<!>.door(1).open() } returns true

    every { car.door(any()).open() } returns true
}
