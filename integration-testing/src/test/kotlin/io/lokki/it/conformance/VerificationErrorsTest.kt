package io.lokki.it.conformance

import io.lokki.clearMocks
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.fail

class VerificationErrorsTest {
    val mock = mock<MockCls>("mock")
    val openMock = mock<OpenMockCls>("mock")

    private fun expectVerificationError(
        vararg messages: String,
        block: () -> Unit,
    ) {
        try {
            clearMocks(mock, openMock)
            block()
            fail("Block should throw verification failure")
        } catch (ex: AssertionError) {
            if (messages.any { !ex.message!!.contains(it) }) {
                fail("Bad message: " + ex.message)
            }
        }
    }

    @Test
    fun notMatchingArguments() {
        expectVerificationError(
            "Only one matching call to ",
            "but arguments are not matching",
            CALL_SITES,
        ) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)

            verify { mock.otherOp(1, 3) }
        }
    }

    @Test
    fun notMatchingArgumentsWithSameMethod() {
        expectVerificationError("No matching calls found.", "Calls to same method", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)
            mock.otherOp(1, 4)

            verify { mock.otherOp(1, 3) }
        }
    }

    @Test
    fun wasNotCalledToSameMock() {
        expectVerificationError("was not called", "Calls to same mock", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)

            verify { mock.manyArgsOp(a = true, b = false) }
        }
    }

    @Test
    fun wasNotCalled() {
        expectVerificationError("was not called") {
            verify { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun someMatchingCallsFoundButNotAll() {
        expectVerificationError("2 matching calls found, but needs at least 3 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)
            mock.otherOp(1, 2)

            verify(atLeast = 3) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun oneMatchingCallFoundButNeedMore() {
        expectVerificationError("One matching call found, but needs at least 3 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)

            verify(atLeast = 3) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun someMatchingCallsFoundButTooMuch() {
        expectVerificationError("4 matching calls found, but needs at least 2 and at most 3 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)
            mock.otherOp(1, 2)
            mock.otherOp(1, 2)
            mock.otherOp(1, 2)

            verify(atLeast = 2, atMost = 3) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun oneMatchingCallFoundButNeedMoreInRange() {
        expectVerificationError("One matching call found, but needs at least 2 and at most 3 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)

            verify(atLeast = 2, atMost = 3) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun someMatchingCallsFoundButNeedExactMatch() {
        expectVerificationError("2 matching calls found, but needs exactly 3 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)
            mock.otherOp(1, 2)

            verify(exactly = 3) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun oneMatchingCallFoundButNeedExactMatch() {
        expectVerificationError("One matching call found, but needs exactly 2 calls", CALL_SITES) {
            every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }

            mock.otherOp(1, 2)

            verify(exactly = 2) { mock.otherOp(1, 2) }
        }
    }

    @Test
    fun argumentsNotMatchingTraceIsTakenFromTheCallThatIsDescribed() {
        clearMocks(mock)

        every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }
        every { mock.manyArgsOp() } returns 0.0

        mock.manyArgsOp()
        mock.otherOp(1, 2)

        try {
            verify { mock.otherOp(1, 3) }
            fail("Block should throw verification failure")
        } catch (ex: AssertionError) {
            val message = ex.message!!
            val trace = message.substringAfter("Stack trace:", "")

            if (!message.contains("but arguments are not matching")) {
                fail("Bad message: " + message)
            }
            if (trace.isNotEmpty() && trace.contains("manyArgsOp")) {
                fail("Stack trace should belong to the call being described: " + message)
            }
        }
    }

    @Test
    fun oneMatchingCallFoundReportsThatCallAndNotAnotherCallOnTheSameMock() {
        clearMocks(mock)

        every { mock.otherOp(1, any()) } answers { 2 + firstArg<Int>() }
        every { mock.manyArgsOp() } returns 0.0

        mock.manyArgsOp()
        mock.otherOp(1, 2)

        try {
            verify(exactly = 2) { mock.otherOp(1, 2) }
            fail("Block should throw verification failure")
        } catch (ex: AssertionError) {
            val message = ex.message!!
            val reportedCall =
                message.lineSequence().firstOrNull { it.startsWith("Call: ") }
                    ?: fail("Message has no reported call: " + message)

            if (!message.contains("One matching call found, but needs exactly 2 calls")) {
                fail("Bad message: " + message)
            }
            if (!reportedCall.contains("otherOp")) {
                fail("Reported call should be the verified one: " + message)
            }
            if (reportedCall.contains("manyArgsOp")) {
                fail("Reported call should not be an unrelated call to the same mock: " + message)
            }
        }
    }

    data class IntWrapper(
        val data: Int,
    )

    interface MockCls {
        fun otherOp(
            a: Int = 1,
            b: Int = 2,
        ): Int

        fun manyArgsOp(
            a: Boolean = true,
            b: Boolean = true,
            c: Byte = 1,
            d: Byte = 2,
            e: Short = 3,
            f: Short = 4,
            g: Char = 5.toChar(),
            h: Char = 6.toChar(),
            i: Int = 7,
            j: Int = 8,
            k: Long = 9,
            l: Long = 10,
            m: Float = 10.0f,
            n: Float = 11.0f,
            o: Double = 12.0,
            p: Double = 13.0,
            q: String = "14",
            r: String = "15",
            s: IntWrapper = IntWrapper(16),
            t: IntWrapper = IntWrapper(17),
        ): Double
    }

    interface OpenMockCls {
        fun op(
            a: Int = 1,
            b: Int = 2,
        ): Int
    }

    private companion object {
        const val CALL_SITES = "Call sites are not recorded"
    }
}
