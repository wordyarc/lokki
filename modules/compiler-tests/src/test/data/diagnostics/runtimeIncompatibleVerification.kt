// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: Verification.kt
package io.lokki.internal

import io.lokki.Matcher
import io.lokki.Ordering

class Verification(
    ordering: Ordering = Ordering.UNORDERED,
    inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    timeoutMillis: Long = 0,
) {
    fun expect(state: Any, key: String, matchers: Array<Matcher<Any?>>) {}

    fun check() {}
}

// FILE: test.kt
package test

import io.lokki.mock
import io.lokki.verify

interface Car {
    fun drive(): Int
}

fun test() {
    val car = mock<Car>()
    verify(timeout = 100) { car.drive() }
}
