// RUN_PIPELINE_TILL: FIR2IR
// RENDER_IR_DIAGNOSTICS_FULL_TEXT

// FILE: LokkiIntrinsic.kt
package io.lokki.internal

annotation class LokkiIntrinsic(val kind: Kind) {
    enum class Kind {
        MOCK_CONSTRUCTOR,
    }
}

// FILE: test.kt
import io.lokki.internal.LokkiIntrinsic

@LokkiIntrinsic(LokkiIntrinsic.Kind.MOCK_CONSTRUCTOR)
fun <T> mockConstructor(value: T): T = value

fun test() {
    <!LOKKI_UNSUPPORTED!>mockConstructor("a")<!>
}
