package io.lokki.it.conformance

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.clearConstructorMock
import io.lokki.every
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.verify
import io.lokki.junit5.LokkiExtension
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(LokkiExtension::class)
class ConstructorMockVerificationTest {
    @Test
    fun zeroCallsPassesWhenNothingWasConstructed() {
        mockConstructor(ConstructedPlain::class)

        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
    }

    @Test
    fun zeroCallsPassesWhenNothingWasCalledAfterStubbing() {
        mockConstructor(ConstructedPlain::class)

        every { anyConstructed<ConstructedPlain>().op(1, 2) } returns 4

        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
    }

    @Test
    fun zeroCallsPassesWhenConstructedInstanceWasNeverCalled() {
        mockConstructor(ConstructedPlain::class)

        ConstructedPlain()

        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
    }

    @Test
    fun zeroCallsPassesAfterConstructorMockWasCleared() {
        mockConstructor(ConstructedPlain::class)

        every { anyConstructed<ConstructedPlain>().op(1, 2) } returns 4
        ConstructedPlain().op(1, 2)

        clearConstructorMock(ConstructedPlain::class)

        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
    }

    @Test
    fun zeroCallsFailsWhenConstructedInstanceWasCalled() {
        mockConstructor(ConstructedPlain::class)

        every { anyConstructed<ConstructedPlain>().op(1, 2) } returns 4
        ConstructedPlain().op(1, 2)

        val error =
            assertFailsWith<AssertionError> {
                verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
            }
        val message = error.message ?: return

        assertTrue("constructed instances" in message)
        assertTrue("op(" in message)
    }

    @Test
    fun zeroCallsFailsWhenConstructionsHappenedBeforeAnyPlaceholderUsage() {
        mockConstructor(ConstructedPlain::class)

        ConstructedPlain().op(1, 2)

        val error =
            assertFailsWith<AssertionError> {
                verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
            }
        val message = error.message ?: return

        assertTrue("constructed instances" in message)
    }

    @Test
    fun zeroCallsChecksEachRequestedPlaceholderType() {
        mockConstructor(ConstructedPlain::class)
        mockConstructor(ConstructedOther::class)

        verify(exactly = 0) {
            anyConstructed<ConstructedPlain>()
            anyConstructed<ConstructedOther>()
        }

        ConstructedOther().execute()

        assertFailsWith<AssertionError> {
            verify(exactly = 0) {
                anyConstructed<ConstructedPlain>()
                anyConstructed<ConstructedOther>()
            }
        }
    }

    @Test
    fun barePlaceholderWithOtherBoundsStillReportsMissingCalls() {
        mockConstructor(ConstructedPlain::class)

        val error =
            assertFailsWith<LokkiException> {
                verify(exactly = 1) { anyConstructed<ConstructedPlain>() }
            }
        val message = error.message ?: return

                assertTrue("exactly = 0" in message)
    }

    @Test
    fun wasNotCalledKeepsItsSemantics() {
        mockConstructor(ConstructedPlain::class)

        val instance = ConstructedPlain()

        verify { instance wasNot Called }

        every { anyConstructed<ConstructedPlain>().op(1, 2) } returns 4
        instance.op(1, 2)

        assertFailsWith<AssertionError> {
            verify { instance wasNot Called }
        }
    }

    @Test
    fun barePlaceholderWasNotCalledChecksAllInstances() {
        mockConstructor(ConstructedPlain::class)

        verify { anyConstructed<ConstructedPlain>() wasNot Called }

        ConstructedPlain().op(1, 2)

        assertFailsWith<AssertionError> {
            verify { anyConstructed<ConstructedPlain>() wasNot Called }
        }
    }

    @Test
    fun callVerificationOnPlaceholderIsUnchanged() {
        mockConstructor(ConstructedPlain::class)

        every { anyConstructed<ConstructedPlain>().op(1, 2) } returns 4

        verify(exactly = 0) { anyConstructed<ConstructedPlain>().op(1, 2) }

        ConstructedPlain().op(1, 2)

        verify { anyConstructed<ConstructedPlain>().op(1, 2) }

        assertFailsWith<AssertionError> {
            verify(exactly = 0) { anyConstructed<ConstructedPlain>().op(1, 2) }
        }
    }

    @Test
    fun aggregateZeroCallsDoesNotAffectRegularMockVerification() {
        mockConstructor(ConstructedPlain::class)

        val regular = mock<ConstructedRegular>()

        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }

        every { regular.op(any(), any()) } returns 1
        regular.op(1, 2)

        verify { regular.op(1, 2) }
        verify(exactly = 0) { anyConstructed<ConstructedPlain>() }
    }
}
