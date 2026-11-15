package io.lokki.it

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.checkUnnecessaryStub
import io.lokki.clearMocks
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import io.lokki.verify
import io.lokki.verifyCount
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
                "but arguments are not matching:\n[0]: argument: 1, matcher: eq(2), result: -\n\n" +
                "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true",
            assertFailsWith<AssertionError> { verify { car.drive(2) } }.message,
        )
    }

    @Test
    fun assertionErrorsOfWithArgAreReported() {
        val car = mock<Car>()
        every { car.drive(any()) } returns "left"

        Garage(car).leave()

        verify { car.drive(withArg { assertEquals(1, it) }) }
        val error = assertFailsWith<AssertionError> { verify { car.drive(withArg { assertEquals(2, it) }) } }
        val assertion = error.suppressedExceptions.single()
        assertEquals("expected: <2> but was: <1>", assertion.message)
        assertEquals(
            "Verification failed: call 1 of 1: $car.drive(matcher()). Only one matching call to $car/drive happened, " +
                "but arguments are not matching:\n[0]: argument: 1, matcher: matcher(), result: -\n\n" +
                "Call sites are not recorded; rerun with -Dlokki.recordStackTraces=true\n\n" +
                "Assertion errors in matchers:\n1) $car.drive(1), argument [0]: $assertion",
            error.message,
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

    @Test
    fun chainedCallIsVerifiedWithItsLinks() {
        val car = mock<Car>()
        val radio = mock<Radio>()
        every { car.radio } returns radio
        every { radio.tune(any(), any()) } returns "news"

        Dashboard(radio).news()

        assertEquals(
            "Verification failed: call 1 of 2: $car.getRadio() was not called",
            assertFailsWith<AssertionError> { verify { car.radio.tune(101) } }.message,
        )
        Garage(car).news()
        verify(exactly = 2) { car.radio.tune(101) }
        confirmVerified(car, radio)
    }

    @Test
    fun chainedCallIsVerifiedOnMockThatLinkReturnedToMain() {
        val car = mock<Car>()
        val first = mock<Radio>()
        val second = mock<Radio>()
        every { car.radio } returnsMany listOf(first, second)
        every { first.tune(any(), any()) } returns "news"

        Garage(car).news()

        verify(exactly = 1) { car.radio.tune(101) }
        assertFailsWith<AssertionError> { verify(exactly = 0) { car.radio.tune(101) } }
        assertFailsWith<AssertionError> { verify(inverse = true) { car.radio.tune(101) } }
        confirmVerified(car, first, second)
    }

    @Test
    fun verifiedCallsAreConfirmedAndCleared() {
        val car = mock<Car>()
        every { car.drive(any()) } returns "left"
        every { car.plate } returns "A001"
        val garage = Garage(car)

        garage.leave()
        garage.plate()
        verify { car.drive(1) }

        assertFailsWith<AssertionError> { confirmVerified(car) }
        verify { car.plate }
        confirmVerified(car, clear = true)
        checkUnnecessaryStub(car)
        clearMocks(car)
        assertFailsWith<LokkiException> { garage.leave() }
        checkUnnecessaryStub(car)
    }

    @Test
    fun excludedCallsAreNotRecorded() {
        val car = mock<Car>()
        val radio = mock<Radio>()
        every { car.drive(any()) } returns "left"
        every { car.plate } returns "A001"
        val garage = Garage(car)

        garage.plate()
        excludeRecords { car.plate }
        garage.plate()
        garage.leave()

        verifyCount {
            0 * { car.plate }
            1 * { car.drive(1) }
        }
        confirmVerified(car)
        verify { radio wasNot Called }
    }
}
