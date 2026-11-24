package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
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
            "no answer found for $car.drive(1) among the configured answers: ()\n" +
                "Called member: Car.drive(Int): String",
            assertFailsWith<LokkiException> { garage.leave() }.message,
        )
        assertEquals(
            "no answer found for $car.getPlate() among the configured answers: ()\n" +
                "Called member: Car.getPlate(): String",
            assertFailsWith<LokkiException> { garage.plate() }.message,
        )
    }

    @Test
    fun additionalInterfaceIsMockedForMemberCalledFromMain() {
        val car = mock<Car>(moreInterfaces = arrayOf(Radio::class))
        every { (car as Radio).tune(101) } returns "news"

        assertEquals("news", Dashboard(car as Radio).news())
        verify { car.tune(101, volume = 5) }
    }

    @Test
    fun relaxedMockAnswersMembersCalledFromMainWithDefaults() {
        val car = mock<Car>(relaxed = true)
        val garage = Garage(car)

        assertEquals("", garage.leave())
        assertEquals("", garage.plate())
        assertEquals(
            "relaxed mock has no default for Radio returned by $car.getRadio(); stub the call " +
                "or annotate MockInterfaceTest.kt, which creates the mock, with @file:RelaxedChildMocks",
            assertFailsWith<LokkiException> { garage.news() }.message,
        )
    }

    @Test
    fun strictMockHasNoAnswerForMemberOfNestedInterface() {
        val engine: Engine = mock()

        assertEquals(
            "no answer found for $engine.start() among the configured answers: ()\n" +
                "Called member: Engine.start(): Boolean",
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
