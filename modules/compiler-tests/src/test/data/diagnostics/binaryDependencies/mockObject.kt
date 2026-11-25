// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// MOCKABLE: sample.mocked.**
// FILE: clock.kt
package sample.mocked

import androidx.compose.runtime.Composable

object Clock {
    const val LIMIT = 1

    lateinit var name: String

    @JvmField
    val field: String = ""

    fun now(): Long = 1

    <!NOTHING_TO_INLINE!>inline<!> fun inlined(): Int = 1

    val <T> List<T>.second: T
        get() = this[1]

    external fun native(): Int

    @Composable
    fun composable(): Int = 1

    val tagged: Int
        @Composable get() = 1

    override fun toString(): String = "clock"
}

// FILE: composable.kt
package androidx.compose.runtime

annotation class Composable

// FILE: registry.kt
package sample.plain

object Registry {
    fun lookup(): String = ""
}

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mockObject
import sample.mocked.Clock
import sample.plain.Registry

fun test() {
    mockObject(Clock)
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Registry<!>)
    every { Clock.now() } returns 2
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>inlined<!>() } returns 2
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>LIMIT<!> } returns 2
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>name<!> } returns ""
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>field<!> } returns ""
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>native<!>() } returns 2
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>composable<!>() } returns 2
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>tagged<!> } returns 2
    with(Clock) { every { listOf(1, 2).<!LOKKI_NOT_INTERCEPTABLE!>second<!> } returns 2 }
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>toString<!>() } returns ""
    every { Registry.<!LOKKI_NOT_INTERCEPTABLE!>lookup<!>() } returns ""
}

fun generated(redirect: Clock.<!DEPRECATION_ERROR!>LokkiRedirect<!>?) {
    Clock.<!UNRESOLVED_REFERENCE!>`lokki$redirect`<!>
    Clock.<!UNRESOLVED_REFERENCE!>`now$lokkiOriginal`<!>()
}
