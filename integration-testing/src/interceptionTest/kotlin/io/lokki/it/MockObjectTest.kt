package io.lokki.it

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.isMock
import io.lokki.it.mockable.Clock
import io.lokki.it.mockable.Ticket
import io.lokki.junit5.LokkiExtension
import io.lokki.mockObject
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@ExtendWith(LokkiExtension::class)
class MockObjectTest {
    @Test
    fun codeUnderTestCallsTheStubbedObject() {
        mockObject(Clock)
        every { Clock.now() } returns 42

        assertEquals(42, Ticket.issue().issued)
        verify { Clock.now() }
    }

    @Test
    fun otherThreadsSeeTheRedirect() {
        mockObject(Clock)
        every { Clock.now() } returns 7

        var issued = 0L
        thread { issued = Ticket.issue().issued }.join()

        assertEquals(7, issued)
    }

    @Test
    fun companionPropertyIsStubbed() {
        mockObject(Ticket)
        every { Ticket.prefix } returns "X"

        assertEquals("X5", Ticket(5).code())
    }

    @ParameterizedTest
    @ValueSource(longs = [1, 2, 3])
    fun everyInvocationMocksTheObjectAnew(issued: Long) {
        assertFalse(isMock(Clock))
        mockObject(Clock)
        every { Clock.now() } returns issued

        assertEquals(issued, Ticket.issue().issued)
        verify(exactly = 1) { Clock.now() }
    }

    @Test
    fun stubMocksTheObject() {
        every { Clock.now() } returns 3

        assertEquals(3, Ticket.issue().issued)
        verify { Clock.now() }
    }

    @Test
    fun verificationWithoutMockObjectFails() {
        val exception = assertFailsWith<LokkiException> { verify { Clock.now() } }

        assertEquals("Clock is not mocked in the current scope; call mockObject(Clock)", exception.message)
    }
}
