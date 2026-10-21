package io.lokki.it

import io.lokki.every
import io.lokki.it.mockable.Odometer
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class MockableClassTest {
    @Test
    fun finalClassOfMockablePackageIsMocked() {
        val odometer = mock<Odometer>()
        every { odometer.read() } returns 120

        assertEquals(120, odometer.read())
        verify { odometer.read() }
    }

    @Test
    fun originalOfOpenedMemberCallsTheStubbedMember() {
        val odometer = mock<Odometer>()
        every { odometer.read() } returns 120
        every { odometer.trip(any()) } answers { callOriginal() }

        assertEquals(20, odometer.trip(100))
    }
}
