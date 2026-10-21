// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: LokkiIntrinsic.kt
package io.lokki.internal

annotation class LokkiIntrinsic(val kind: Kind) {
    enum class Kind {
        MOCK_OBJECT,
    }
}

// FILE: test.kt
import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_OBJECT)
fun <T> mockObject(value: T): T = value

fun test() {
    <!LOKKI_UNSUPPORTED!>mockObject("a")<!>
}
