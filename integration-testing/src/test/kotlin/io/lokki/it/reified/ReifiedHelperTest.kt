package io.lokki.it.reified

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class ReifiedHelperTest {
    @Test
    fun helperFromAnotherFileCreatesAMock() = lokkiScope {
        val wheel = relaxedMock<Wheel>()
        assertEquals(0, wheel.size())
        every { wheel.size() } returns 17
        assertEquals(17, wheel.size())
        verify(exactly = 2) { wheel.size() }
    }

    @Test
    fun nestedHelperKeepsTypeArgumentsApart() = lokkiScope {
        assertEquals("", nestedHelper<Crate<String>>().item())
        assertEquals(0, nestedHelper<Crate<Int>>().item())
    }
}
