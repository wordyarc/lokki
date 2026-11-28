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

data class Settings(val enabled: Boolean, val limit: Int)

object Clock {
    fun hour(): Int = 12
}

fun newId(): String = "id"

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.every
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.mockObject
import io.lokki.mockStatic
import sample.Clock
import sample.Aliased
import sample.Engine
import sample.Fuel
import sample.Locked
import sample.newId
import sample.Registry
import sample.Rpm
import sample.Settings
import sample.Tagged

class Window {
    fun open(): String = "open"
}

fun test() {
    val engine = mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Engine<!>>()
    every { engine.start() } returns "mocked"
    every { engine.id() } returns 2
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Aliased<!>>()
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Tagged<!>>()
    mock<Locked>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Rpm<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Fuel<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Registry<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Window<!>>()
    mock<Settings>()
    mockObject(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Clock<!>)
    every { Clock.<!LOKKI_NOT_INTERCEPTABLE!>hour<!>() } returns 9
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>::newId<!>)
    mockConstructor(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Engine::class<!>)
}
