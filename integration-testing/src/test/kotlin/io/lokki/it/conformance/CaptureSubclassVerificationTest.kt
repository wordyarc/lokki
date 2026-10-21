package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import io.lokki.verifyCount
import io.lokki.verifyOrder
import io.lokki.verifySequence
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CaptureSubclassVerificationTest {
    interface Interface

    class Subclass1 : Interface

    class Subclass2 : Interface

    interface Service {
        fun method(obj: Interface)
    }

    @Test
    fun `test unordered`() {
        val service =
            mock<Service> {
                every { method(any()) } just Runs
            }

        service.method(Subclass1())
        service.method(Subclass2())

        val slot = slot<Subclass2>()
        verify(exactly = 1) { service.method(capture(slot)) }
        assertTrue(slot.isCaptured)
        assertIs<Subclass2>(slot.captured)
    }

    @Test
    fun `test ordered`() {
        val service =
            mock<Service> {
                every { method(any()) } just Runs
            }

        service.method(Subclass1())
        service.method(Subclass2())

        val slot = slot<Subclass2>()
        verifyOrder {
            service.method(any())
            service.method(capture(slot))
        }
        assertTrue(slot.isCaptured)
        assertIs<Subclass2>(slot.captured)
    }

    @Test
    fun `test sequence`() {
        val service =
            mock<Service> {
                every { method(any()) } just Runs
            }

        service.method(Subclass1())
        service.method(Subclass2())

        val slot = slot<Subclass2>()
        verifySequence {
            service.method(any())
            service.method(capture(slot))
        }
        assertTrue(slot.isCaptured)
        assertIs<Subclass2>(slot.captured)
    }

    @Test
    fun `test count`() {
        val service =
            mock<Service> {
                every { method(any()) } just Runs
            }

        service.method(Subclass1())
        service.method(Subclass2())

        val slot = slot<Subclass2>()
        verifyCount {
            2 * { service.method(any()) }
            1 * { service.method(capture(slot)) }
        }
        assertTrue(slot.isCaptured)
        assertIs<Subclass2>(slot.captured)
    }
}
