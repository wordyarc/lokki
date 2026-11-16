// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.MatcherScope
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.verify

interface Door {
    fun open(): Boolean
}

interface Box<T> {
    fun get(index: Int): T
}

abstract class Garage {
    fun door(index: Int): Door = TODO()
}

interface Car {
    val doors: List<Door>
    fun door(index: Int): Door
    fun seat(row: Int, place: Int): Door
}

fun pick(index: Int): Car = TODO()

fun MatcherScope.front(): Int = less(2)

fun <T : Door> test(car: Car, garage: Garage, box: Box<T>) {
    every { car.door(any()).open() } returns true
    every { car.door(front()).open() } returns true
    every { car.doors[any()].open() } returns true
    verify { car.door(any()).open() }
    excludeRecords { car.door(any()).open() }

    verify { <!LOKKI_TYPE_NOT_MOCKABLE!>box.get(any())<!>.open() }
    every { garage.<!LOKKI_NOT_INTERCEPTABLE!>door<!>(any()).open() } returns true
    every { car.door(1 + <!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any<Int>()<!>).open() } returns true
    every { car.seat(1, <!LOKKI_ALL_ANY_NOT_FIRST!>allAny()<!>).open() } returns true
    every { pick(<!LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION!>any()<!>).door(1).open() } returns true
}
