// FILE: model.kt
package sample

import io.lokki.mock

interface Car {
    fun drive(speed: Int): Int
}

interface Garage {
    fun car(): Car
}

class Fixture {
    val garage = mock<Garage>()
}

// FILE: chained.kt
@file:RelaxedChildMocks
@file:Suppress("LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION", "LOKKI_UNSUPPORTED")

package sample

import io.lokki.LokkiException
import io.lokki.RelaxedChildMocks
import io.lokki.every

fun stubbed(fixture: Fixture): String? =
    try {
        every { fixture.garage.car().drive(any<Int>() + 1) } returns 1
        "stubbed"
    } catch (e: LokkiException) {
        e.message
    }

// FILE: test.kt
@file:Suppress("LOKKI_MATCHER_NOT_IN_ARGUMENT_POSITION", "LOKKI_UNSUPPORTED")

package sample

import io.lokki.LokkiException
import io.lokki.verify

private fun verified(fixture: Fixture): String? =
    try {
        verify { fixture.garage.car().drive(any<Int>() + 1) }
        "verified"
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    val expected = "lokki compiler plugin is not applied to this compilation"
    val fixture = Fixture()
    val verified = verified(fixture)
    if (verified != expected) return "verify: $verified"
    val stubbed = stubbed(fixture)
    if (stubbed != expected) return "every: $stubbed"
    return "OK"
}
