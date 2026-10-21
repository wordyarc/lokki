// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.verify

interface Door {
    fun open(): Boolean
}

interface Car {
    fun door(index: Int): Door
}

fun test(car: Car) {
    every { car.<!LOKKI_UNSUPPORTED!>door(any())<!>.open() } returns true
    verify { car.<!LOKKI_UNSUPPORTED!>door(any())<!>.open() }
    excludeRecords { car.<!LOKKI_UNSUPPORTED!>door(any())<!>.open() }

    every { car.door(1).open() } returns true
}
