// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: LokkiIntrinsic.kt
package io.lokki.internal

annotation class LokkiIntrinsic(val kind: Kind) {
    enum class Kind {
        FUTURE_DSL,
    }
}

// FILE: test.kt
import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.FUTURE_DSL)
fun <T> futureDsl(value: T): T = value

fun test() {
    <!LOKKI_UNSUPPORTED!>futureDsl("a")<!>
}
