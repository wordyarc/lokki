// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT
// CHECK_SOURCELESS_DIAGNOSTICS

// FILE: StaticInitializer.kt
package io.lokki.internal

object StaticInitializer {
    fun enter(depth: Int) {}

    fun exit() {}
}

// FILE: test.kt
package test

val started = System.nanoTime()
