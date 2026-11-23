// MODULE: main
// MOCKABLE: sample.**
// FILE: statics.kt
package sample

object Clock {
    @JvmStatic
    fun now(): Long = 1
}

class Settings {
    companion object {
        @JvmStatic
        fun mode(): String = "eco"
    }
}

// MODULE: test(main)
// FILE: test.kt
import io.lokki.clearStaticMock
import io.lokki.every
import io.lokki.isMock
import io.lokki.lokkiScope
import io.lokki.mockStatic
import io.lokki.unmockStatic
import io.lokki.verify
import sample.Clock
import sample.Settings

fun box(): String = lokkiScope {
    mockStatic(Clock::class, Settings::class)
    if (!isMock(Clock::class, regular = false, staticMock = true)) return@lokkiScope "object is static"
    if (!isMock(Settings::class, regular = false, staticMock = true)) return@lokkiScope "companion is static"
    if (isMock(Clock, regular = false, objectMock = true)) return@lokkiScope "not an object mock"
    every { Clock.now() } returns 7
    every { Settings.mode() } returns "sport"
    if (Clock.now() != 7L || Settings.mode() != "sport") return@lokkiScope "stub"
    verify { Clock.now() }
    clearStaticMock(Clock::class, answers = false)
    if (Clock.now() != 7L) return@lokkiScope "answers are kept"
    verify(exactly = 1) { Clock.now() }
    clearStaticMock(Clock::class)
    if (Clock.now() != 1L) return@lokkiScope "answers are cleared"
    unmockStatic(Clock::class, Settings::class)
    if (Settings.mode() != "eco") return@lokkiScope "unmock"
    if (isMock(Clock::class, regular = false, staticMock = true)) return@lokkiScope "unmocked"
    var inBlock = 0L
    mockStatic(Clock::class) {
        every { Clock.now() } returns 9
        inBlock = Clock.now()
    }
    if (inBlock != 9L || Clock.now() != 1L) return@lokkiScope "block"
    "OK"
}
