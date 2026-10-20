package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MockClassTest {
    @Test
    fun internalMemberOfMainIsMockedAndCalledFromMain() {
        val car = mock<Car>()
        val mechanic = mock<Mechanic>()
        every { mechanic.diagnose(car) } returns "fine"
        every { mechanic.repair(car) } returns "nothing to repair"

        assertEquals("fine, nothing to repair", mechanic.service(car))
        verify { mechanic.diagnose(car) }
    }

    @Test
    fun strictMockOfClassHasNoAnswer() {
        val car = mock<Car>()
        val mechanic = mock<Mechanic>()

        val message = assertFailsWith<LokkiException> { mechanic.service(car) }.message.orEmpty()
        assertTrue(message.startsWith("no answer found for Mechanic(#"), message)
        assertTrue(message.contains(".diagnose(Car(#"), message)
    }

    @Test
    fun answerCallsTheOriginalOfOpenMember() {
        val car = mock<Car>()
        val mechanic = mock<Mechanic>()
        every { car.plate } returns "A-1"
        every { mechanic.repair(car) } answers { callOriginal().uppercase() }

        assertEquals("REPAIRED A-1", mechanic.repair(car))
    }

    @Test
    fun relaxedMockOfClassAnswersInternalMemberOfMain() {
        val car = mock<Car>()
        val mechanic = mock<Mechanic>(relaxed = true)

        assertEquals(", ", mechanic.service(car))
    }

    @Test
    fun finalMemberRunsTheOriginalWithoutConstructor() {
        assertEquals("serviced by null", mock<Mechanic>().signature())
    }
}
