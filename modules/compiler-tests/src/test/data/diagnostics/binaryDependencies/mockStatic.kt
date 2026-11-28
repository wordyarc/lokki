// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// INTERCEPTABLE: sample.mocked.**
// FILE: clock.kt
package sample.mocked

import androidx.compose.runtime.Composable

const val LIMIT = 1

@JvmField
val field: String = ""

val <T> List<T>.second: T
    get() = this[1]

external fun native(): Int

@Composable
fun composable(): Int = 1

lateinit var name: String

fun now(): Long = 1

<!NOTHING_TO_INLINE!>inline<!> fun inlined(): Int = 1

// FILE: composable.kt
package androidx.compose.runtime

annotation class Composable

// FILE: registry.kt
package sample.plain

fun lookup(): String = ""

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mockStatic
import sample.mocked.LIMIT
import sample.mocked.composable
import sample.mocked.field
import sample.mocked.inlined
import sample.mocked.name
import sample.mocked.native
import sample.mocked.now
import sample.mocked.second
import sample.plain.lookup

fun test() {
    mockStatic(::now)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::lookup<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::LIMIT<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::name<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::inlined<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::field<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>List<Int>::second<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::native<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::composable<!>)
    every { now() } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>lookup<!>() } returns ""
    every { <!LOKKI_NOT_INTERCEPTABLE!>inlined<!>() } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>LIMIT<!> } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>field<!> } returns ""
    every { listOf(1, 2).<!LOKKI_NOT_INTERCEPTABLE!>second<!> } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>native<!>() } returns 2
    every { <!LOKKI_NOT_INTERCEPTABLE!>composable<!>() } returns 2
}

fun generated(redirect: sample.mocked.<!DEPRECATION_ERROR!>`ClockKt$LokkiRedirect`<!>?) {
    sample.mocked.<!UNRESOLVED_REFERENCE!>`now$lokkiOriginal`<!>()
}
