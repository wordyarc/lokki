// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.checkEquals
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyCount

interface Bus {
    fun register(listener: Any)
    fun unregister(listener: Any)
    fun publish(id: Int, payload: Int): String
    fun flag(enabled: Boolean, payload: Any?): String
}

object Slots {
    val listener = slot<Any>()
}

class Fixture {
    val listener = slot<Any>()
    val other = slot<Any>()

    fun test(bus: Bus) {
        verify {
            bus.register(capture(listener))
            bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>listener.captured<!>)
        }

        verify {
            bus.register(capture(listener))
            bus.unregister(other.captured)
        }
    }
}

fun test(bus: Bus, fixture: Fixture, other: Fixture, listeners: MutableList<Any>) {
    val listener = slot<Any>()
    val id = slot<Int>()
    val payload = slot<Any?>()
    verify {
        bus.register(capture(listener))
        bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>listener.captured<!>)
    }
    verify {
        bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>listener.captured<!>)
        bus.register(capture(listener))
    }
    verify { bus.publish(capture(id), <!LOKKI_SLOT_READ_BEFORE_CAPTURE!>id.captured<!>) }
    verify { bus.flag(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>payload.isCaptured<!>, captureNullable(payload)) }
    verify { bus.flag(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>payload.isNull<!>, captureNullable(payload)) }
    verify { bus.publish(capture(id), withArg { checkEquals(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>id.captured<!>) }) }
    verify {
        bus.register(capture(fixture.listener))
        bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>fixture.listener.captured<!>)
    }
    verify {
        bus.register(capture(Slots.listener))
        bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>Slots.listener.captured<!>)
    }
    verifyCount {
        1 * {
            bus.register(capture(listener))
            bus.unregister(<!LOKKI_SLOT_READ_BEFORE_CAPTURE!>listener.captured<!>)
        }
    }
    every { bus.publish(capture(id), <!LOKKI_SLOT_READ_BEFORE_CAPTURE!>id.captured<!>) } returns "a"
    every { bus.publish(capture(id), match { it > <!LOKKI_SLOT_READ_BEFORE_CAPTURE!>id.captured<!> }) } returns "a"

    verify { bus.register(capture(listener)) }
    verify { bus.unregister(listener.captured) }
    every { bus.publish(capture(id), any()) } answers { "${id.captured}" }
    verify {
        bus.register(capture(fixture.listener))
        bus.unregister(other.listener.captured)
    }
    verify {
        bus.register(capture(listeners))
        bus.unregister(listeners.last())
    }
    excludeRecords { bus.publish(capture(id), id.captured) }
}
