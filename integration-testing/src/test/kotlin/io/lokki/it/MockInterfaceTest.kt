package io.lokki.it

import io.lokki.LokkiException
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MockInterfaceTest {
    interface Engine {
        fun start(): Boolean
    }

    @Test
    fun strictMockHasNoAnswerForMemberCalledFromMain() {
        val car = mock<Car>("rental")
        val garage = Garage(car)

        assertTrue(car.toString().startsWith("Car(rental#"))
        assertEquals(
            "no answer found for $car.drive(1) among the configured answers: ()",
            assertFailsWith<LokkiException> { garage.leave() }.message,
        )
        assertEquals(
            "no answer found for $car.getPlate() among the configured answers: ()",
            assertFailsWith<LokkiException> { garage.plate() }.message,
        )
    }

    @Test
    fun strictMockHasNoAnswerForMemberOfNestedInterface() {
        val engine: Engine = mock()

        assertEquals(
            "no answer found for $engine.start() among the configured answers: ()",
            assertFailsWith<LokkiException> { engine.start() }.message,
        )
    }

    @Test
    fun mocksOfOneTypeInOneFileShareTheClassAndHaveIdentityEquality() {
        val first = mock<Car>()
        val second = mock<Car>()

        assertSame(first.javaClass, second.javaClass)
        assertEquals(first, first)
        assertNotEquals(first, second)
    }
}
