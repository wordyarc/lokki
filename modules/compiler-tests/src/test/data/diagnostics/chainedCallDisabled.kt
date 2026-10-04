// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.verify

interface Door {
    val window: Window
    fun open(): Boolean
}

interface Window {
    fun state(): String
}

interface Car {
    val doors: List<Door>
    fun door(index: Int): Door
}

fun MatcherScope.front(): Int = less(2)

fun test(car: Car, provider: () -> Car) {
    every { car.door(<!LOKKI_CHAINED_CALL_DISABLED!>any()<!>).open() } returns true
    every { car.door(<!LOKKI_CHAINED_CALL_DISABLED!>front()<!>).window.state() } returns "up"
    every { car.door(<!LOKKI_CHAINED_CALL_DISABLED!>any()<!>).window.state() } returns "up"
    every { car.doors[<!LOKKI_CHAINED_CALL_DISABLED!>any()<!>].open() } returns true

    every { car.door(1).open() } returns true
    every { car.door(1).window.state() } returns "up"
    every { car.doors[0].open() } returns true
    every { provider().door(1).open() } returns true

    verify { car.door(<!LOKKI_CHAINED_CALL_DISABLED!>any()<!>).open() }
    verify { car.door(1).open() }
}
