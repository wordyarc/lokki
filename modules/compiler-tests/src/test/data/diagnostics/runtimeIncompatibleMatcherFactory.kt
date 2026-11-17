// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: Matchers.kt
package io.lokki.internal

import io.lokki.Matcher

object Matchers {
    fun more(value: Comparable<*>): Matcher<Any?> = throw UnsupportedOperationException()
}

// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mock

interface Car {
    fun drive(speed: Int): Int
}

fun test() {
    val car = mock<Car>()
    every { car.drive(more(1, andEquals = true)) } returns 1
}
