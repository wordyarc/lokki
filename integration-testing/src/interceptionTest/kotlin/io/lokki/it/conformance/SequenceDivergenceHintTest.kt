package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.it.conformance.divergence.SeqCls
import io.lokki.mock
import io.lokki.slot
import io.lokki.spy
import io.lokki.verifyOrder
import io.lokki.verifySequence
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SequenceDivergenceHintTest {
    @Test
    fun sequenceWithUnstubbedCallHintsAtTheDivergencePoint() {
        val mock = mock<SeqCls>()
        every { mock.op(1, 0) } returns 10

        mock.op(1, 0)
        try {
            mock.op(2, 0)
        } catch (_: LokkiException) {
        }

        val failure =
            assertFailsWith<AssertionError> {
                verifySequence {
                    mock.op(1, 0)
                    mock.op(2, 0)
                    mock.op(3, 0)
                }
            }
        val message = failure.message!!

        assertContains(message, "number of calls happened not matching exact number of verification sequence")
        assertContains(message, "had no stubbed answer / threw during execution")
        assertContains(message, "this is likely where the sequence diverged")
        assertContains(message, "op(eq(2), eq(0))")
    }

    @Test
    fun sequenceWithCallThatThrewDuringExecutionHintsAtIt() {
        val mock = mock<SeqCls>()
        every { mock.op(1, 0) } returns 10
        every { mock.op(2, 0) } throws IllegalStateException("boom")

        mock.op(1, 0)
        try {
            mock.op(2, 0)
        } catch (_: IllegalStateException) {
        }

        val failure =
            assertFailsWith<AssertionError> {
                verifySequence {
                    mock.op(1, 0)
                    mock.op(2, 0)
                    mock.op(3, 0)
                }
            }
        val message = failure.message!!

        assertContains(message, "number of calls happened not matching exact number of verification sequence")
        assertContains(message, "had no stubbed answer / threw during execution")
        assertContains(message, "op(eq(2), eq(0))")
    }

    @Test
    fun fullyStubbedSequenceWithArgumentMismatchKeepsMessageWithoutHint() {
        val mock = mock<SeqCls>()
        every { mock.op(1, 0) } returns 10
        every { mock.op(2, 0) } returns 20

        mock.op(1, 0)
        mock.op(2, 0)

        val failure =
            assertFailsWith<AssertionError> {
                verifySequence {
                    mock.op(1, 0)
                    mock.op(9, 9)
                }
            }
        val message = failure.message!!

        assertContains(message, "calls are not exactly matching verification sequence")
        assertFalse(message.contains("had no stubbed answer"), "Message: $message")
    }

    @Test
    fun orderVerificationWithUnstubbedCallAlsoHintsAtTheDivergencePoint() {
        val mock = mock<SeqCls>()
        every { mock.op(1, 0) } returns 10

        mock.op(1, 0)
        try {
            mock.op(2, 0)
        } catch (_: LokkiException) {
        }

        val failure =
            assertFailsWith<AssertionError> {
                verifyOrder {
                    mock.op(1, 0)
                    mock.op(2, 0)
                    mock.op(3, 0)
                }
            }
        val message = failure.message!!

        assertContains(message, "fewer calls happened than demanded by order verification sequence")
        assertContains(message, "had no stubbed answer / threw during execution")
    }

    @Test
    fun fullyStubbedSequenceStillVerifiesAndCapturesAsBefore() {
        val mock = mock<SeqCls>()
        every { mock.op(1, 0) } returns 10
        every { mock.op(2, 0) } returns 20

        mock.op(1, 0)
        mock.op(2, 0)

        val capturedB = slot<Int>()
        verifySequence {
            mock.op(1, 0)
            mock.op(2, capture(capturedB))
        }

        assertEquals(0, capturedB.captured)
    }

    @Test
    fun spyUnstubbedCallAnsweredByOriginalDoesNotHint() {
        val spy = spy<SeqCls>()
        every { spy.op(1, 0) } returns 10

        spy.op(1, 0)
        spy.op(2, 0)

        val failure =
            assertFailsWith<AssertionError> {
                verifySequence {
                    spy.op(1, 0)
                    spy.op(2, 0)
                    spy.op(3, 0)
                }
            }
        val message = failure.message!!

        assertContains(message, "number of calls happened not matching exact number of verification sequence")
        assertFalse(message.contains("had no stubbed answer"), "Message: $message")
    }
}
