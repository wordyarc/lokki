package io.lokki.it

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VerifyInterfaceTest {
    @Test
    fun callsFromMainAreVerified() {
        val car = mock<Car>()
        every { car.drive(any()) } returns "left"
        every { car.plate } returns "A001"
        val garage = Garage(car)

        garage.leave()
        garage.leave()

        verify(exactly = 2) { car.drive(1) }
        verify(exactly = 0) { car.plate }
    }

    @Test
    fun failedVerificationIsAnAssertionError() {
        val car = mock<Car>()
        every { car.drive(any()) } returns "left"

        Garage(car).leave()

        assertEquals(
            "Verification failed: call 1 of 1: $car.drive(eq(2)). Only one matching call to $car/drive happened, " +
                "but arguments are not matching:\n[0]: argument: 1, matcher: eq(2), result: -\n",
            assertFailsWith<AssertionError> { verify { car.drive(2) } }.message,
        )
    }

    @Test
    fun omittedDefaultOfMemberFromMainIsVerifiedByItsValue() {
        val radio = mock<Radio>()
        every { radio.tune(any(), any()) } returns "news"
        val dashboard = Dashboard(radio)

        dashboard.news()

        verify { radio.tune(101) }
        verify(inverse = true) { radio.tune(101, 11) }
    }
}
