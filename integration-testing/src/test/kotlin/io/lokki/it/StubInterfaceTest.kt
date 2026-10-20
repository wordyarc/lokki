package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StubInterfaceTest {
    @Test
    fun stubbedMembersAnswerToMain() {
        val car = mock<Car>()
        every { car.drive(any()) } returns "left"
        every { car.plate } returns "A001"
        val garage = Garage(car)

        assertEquals("left", garage.leave())
        assertEquals("A001", garage.plate())
    }

    @Test
    fun omittedDefaultOfMemberFromMainIsMatchedByItsValue() {
        val radio = mock<Radio>()
        every { radio.tune(any()) } returns "news"
        val dashboard = Dashboard(radio)

        assertEquals("news", dashboard.news())
        assertEquals(
            "no answer found for $radio.tune(101, 11) among the configured answers: ($radio.tune(any(), eq(5)))\n" +
                "Called member: Radio.tune(Int, Int): String",
            assertFailsWith<LokkiException> { dashboard.loudNews() }.message,
        )
    }

    @Test
    fun blockOfMockStubsItsMembers() {
        val car = mock<Car> {
            every { drive(any()) } returns "left"
            every { plate } returns "A001"
        }
        every { car.toString() } returns "rental"
        val garage = Garage(car)

        assertEquals("left", garage.leave())
        assertEquals("A001", garage.plate())
        assertEquals("Garage(rental)", garage.toString())
    }
}
