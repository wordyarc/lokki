package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.it.mockable.stamp
import io.lokki.it.mockable.today
import io.lokki.junit5.LokkiExtension
import io.lokki.mockStatic
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(LokkiExtension::class)
class MockStaticTest {
    @Test
    fun codeUnderTestCallsTheStubbedFunction() {
        mockStatic(::today)
        every { today() } returns "tomorrow"

        assertEquals("issued tomorrow", stamp())
        verify { today() }
    }

    @Test
    fun otherThreadsSeeTheRedirect() {
        mockStatic(::today)
        every { today() } returns "later"

        var stamped = ""
        thread { stamped = stamp() }.join()

        assertEquals("issued later", stamped)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "b"])
    fun everyInvocationMocksTheFunctionAnew(day: String) {
        assertEquals("issued 2026-10-22", stamp())
        mockStatic(::today)
        every { today() } returns day

        assertEquals("issued $day", stamp())
        verify(exactly = 1) { today() }
    }

    @Test
    fun blockFormRemovesTheRedirect() {
        mockStatic(::today) {
            every { today() } returns "inside"
            assertEquals("issued inside", stamp())
        }

        assertEquals("issued 2026-10-22", stamp())
    }

    @Test
    fun stubWithoutMockStaticFails() {
        val exception = assertFailsWith<LokkiException> { every { today() } returns "x" }

        assertEquals("::today is not mocked in the current scope; call mockStatic(::today)", exception.message)
    }
}
