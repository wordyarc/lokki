// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: other/Locked.java
package other;

public abstract class Locked {
    abstract void hidden();

    public boolean open() {
        return false;
    }
}

// FILE: chainedMocksChildMock.kt
package sample

import io.lokki.every
import io.lokki.verify
import other.Locked

interface Door {
    fun open(): Boolean
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
