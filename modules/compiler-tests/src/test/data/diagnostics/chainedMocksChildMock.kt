// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:ChainedMocks

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

fun test(car: Car) {
    every { car.<!LOKKI_TYPE_NOT_MOCKABLE!>locked(any())<!>.open() } returns true
    verify { car.<!LOKKI_TYPE_NOT_MOCKABLE!>locked(any())<!>.open() }

    every { car.door(any()).open() } returns true
}
