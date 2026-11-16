// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: main
// MOCKABLE: sample.**
// FILE: aliased.kt
package sample

import kotlin.jvm.JvmField as Field

class Aliased(@Field val code: String) {
    @Field
    val serial: String = "serial"

    @field:Field
    val tag: String = "tag"
}

// FILE: main.kt
package sample

class Engine(@JvmField val code: String) {
    @JvmField
    val serial: String = "serial"

    var revision: Int = 0
        private set

    inline val doubled: Int
        get() = revision * 2

    fun start(): String = "started"

    final fun id(): Int = 1

    inline fun twice(block: () -> Int): Int = block() + block()

    tailrec fun countdown(n: Int): Int = if (n == 0) 0 else countdown(n - 1)

    private fun secret(): Int = 1
}

class Tagged {
    @field:JvmField
    val tag: String = "tag"
}

final class Locked {
    fun open(): String = "locked"
}

@JvmInline
value class Rpm(val value: Int)

sealed class Fuel {
    fun burn(): String = "burn"
}

object Registry

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mock
import sample.Aliased
import sample.Engine
import sample.Fuel
import sample.Locked
import sample.Registry
import sample.Rpm
import sample.Tagged

class Window {
    fun open(): String = "open"
}

fun test() {
    val engine = mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Engine<!>>()
    every { engine.start() } returns "mocked"
    every { engine.<!LOKKI_NOT_INTERCEPTABLE!>id<!>() } returns 2
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Aliased<!>>()
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Tagged<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Locked<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Rpm<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Fuel<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Registry<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Window<!>>()
}
