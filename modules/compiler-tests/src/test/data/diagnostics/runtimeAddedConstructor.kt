// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: MockSpec.kt
package io.lokki.internal

class MockSpec(val name: String?, val flags: Int, val origin: String) {
    constructor(name: String?, flags: Int) : this(name, flags, "")
}

// FILE: test.kt
package test

import io.lokki.mock

interface Car {
    fun drive(): Int
}

fun test() {
    mock<Car>()
}
