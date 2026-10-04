package io.lokki.it

import io.lokki.LokkiException
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class MockInterfaceTest {
    interface Engine {
        fun start(): Boolean
    }

    @Test
    fun strictMockHasNoAnswerForMemberCalledFromMain() {
        val garage = Garage(mock<Car>())

        assertEquals("no answer found for Car.drive", assertFailsWith<LokkiException> { garage.leave() }.message)
        assertEquals("no answer found for Car.<get-plate>", assertFailsWith<LokkiException> { garage.plate() }.message)
    }

    @Test
    fun strictMockHasNoAnswerForMemberOfNestedInterface() {
        val engine: Engine = mock()

        assertEquals("no answer found for Engine.start", assertFailsWith<LokkiException> { engine.start() }.message)
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
