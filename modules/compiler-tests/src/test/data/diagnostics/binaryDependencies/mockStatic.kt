// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// MOCKABLE: sample.mocked.**
// FILE: clock.kt
package sample.mocked

const val LIMIT = 1

lateinit var name: String

fun now(): Long = 1

<!NOTHING_TO_INLINE!>inline<!> fun inlined(): Int = 1

// FILE: registry.kt
package sample.plain

fun lookup(): String = ""

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mockStatic
import sample.mocked.LIMIT
import sample.mocked.inlined
import sample.mocked.name
import sample.mocked.now
import sample.plain.lookup

fun test() {
    mockStatic(::now)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::lookup<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::LIMIT<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::name<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::inlined<!>)
    every { now() } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>lookup<!>() } returns ""
    every { <!LOKKI_NOT_INTERCEPTABLE!>inlined<!>() } returns 2
}

fun generated(redirect: sample.mocked.<!DEPRECATION_ERROR!>`ClockKt$LokkiRedirect`<!>?) {
    sample.mocked.<!UNRESOLVED_REFERENCE!>`now$lokkiOriginal`<!>()
}
