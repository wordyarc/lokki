// INTERCEPTABLE: fixture.Clock fixture.Engine fixture.now fixture.Marked
// FILE: fixture.kt
package fixture

annotation class Marked

object Clock {
    fun time(): Long = 1

    val zone: String
        get() = "utc"
}

class Engine(private val power: Int) {
    fun start(): String = "started $power"
}

fun now(): String = "real"

fun untouched(): String = "untouched"

@Marked
fun marked(): String = "real marked"

class Holder {
    companion object {
        fun plain(): String = "plain"
    }
}

// FILE: test.kt
package test

import fixture.Clock
import fixture.Engine
import fixture.marked
import fixture.now
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.mockObject
import io.lokki.mockStatic
import io.lokki.verify

fun box(): String =
    lokkiScope {
        mockObject(Clock)
        if (Clock.time() != 1L) return@lokkiScope "Fail original of a test object"
        every { Clock.time() } returns 42
        every { Clock.zone } returns "cet"
        if (Clock.time() != 42L || Clock.zone != "cet") return@lokkiScope "Fail test object"
        verify { Clock.time() }

        val engine = mock<Engine>()
        every { engine.start() } returns "mocked"
        if (engine.start() != "mocked") return@lokkiScope "Fail final test class"

        mockConstructor(Engine::class)
        if (Engine(3).start() != "started 3") return@lokkiScope "Fail original of a test constructor"
        every { anyConstructed<Engine>().start() } returns "constructed"
        if (Engine(1).start() != "constructed") return@lokkiScope "Fail test constructor"
        verify { anyConstructed<Engine>().start() }

        mockStatic(::now, ::marked)
        every { now() } returns "stubbed"
        every { marked() } returns "stubbed marked"
        if (now() != "stubbed") return@lokkiScope "Fail top-level function"
        if (marked() != "stubbed marked") return@lokkiScope "Fail annotated function"
        "OK"
    }
