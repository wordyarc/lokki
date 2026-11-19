// MODULE: main
// MOCKABLE: sample.**
// FILE: clock.kt
package sample

object Clock {
    fun now(): Long = 1

    val zone: String
        get() = "UTC"
}

class Settings {
    companion object {
        fun mode(): String = "eco"
    }
}

fun today(): String = "monday"

val uptime: Long
    get() = 2

// MODULE: test()(main)
// FILE: ObjectTest.kt
package sample

import io.lokki.mockObject

fun mockClock() {
    mockObject(Clock)
}

fun mockSettings() {
    mockObject(Settings)
}

// FILE: StaticTest.kt
package sample

import io.lokki.mockStatic

fun mockToday() {
    mockStatic(::today)
}

fun mockUptime() {
    mockStatic(::uptime)
}
