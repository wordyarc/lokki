// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
// CHECK_SOURCELESS_DIAGNOSTICS

// FILE: MockSpec.kt
package io.lokki.internal

class MockSpec(val name: String?)

// FILE: test.kt
package test

import io.lokki.mock

interface Car {
    fun drive(): Int
}

fun test() {
    mock<Car>()
}
