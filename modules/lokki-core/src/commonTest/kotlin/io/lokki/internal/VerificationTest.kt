package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.Runs
import io.lokki.just
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationTest {
    private val descriptor = MockDescriptor("Repo", ANY_KEYS + arrayOf(FIND, SAVE))
    private val state = MockState(Any(), MockSpec("repo", 0), descriptor)
    private val name = state.name

    init {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "found"
        state.stub<Unit>(SAVE, arrayOf(AnyMatcher)) just Runs
    }

    @Test
    fun testMatchingCallPasses() {
        find(1, false)

        verify(expectation(1))
    }

    @Test
    fun testMethodsOfAnyAndRegistrationAreNotRecorded() {
        state.answer(EQUALS, arrayOf(null))
        state.answer(HASH_CODE, emptyArray())
        state.answer(TO_STRING, emptyArray())
        GeneratedMock.withoutStateChanges { find(1, false) }

        assertEquals(emptyList(), state.records())
    }

    @Test
    fun testCallsAreRecordedInOrderEvenWithoutAnswer() {
        val other = MockState(Any(), MockSpec(null, 0), descriptor)
        find(1, false)
        assertFailsWith<LokkiException> { other.answer(SAVE_MEMBER, arrayOf("x")) }
        find(2, true)

        val records = state.records()
        assertEquals(listOf("$name.find(1, false)", "$name.find(2, true)"), records.map { it.toString() })
        val (first, second) = records
        assertTrue(first.sequenceNumber < other.records().single().sequenceNumber)
        assertTrue(other.records().single().sequenceNumber < second.sequenceNumber)
    }

    @Test
    fun testCallWasNotMade() {
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()) was not called",
            failure(Verification(), expectation(1)),
        )
    }

    @Test
    fun testOtherMembersWereCalled() {
        save("x")

        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()) was not called.\n\n" +
                "Calls to same mock:\n1) $name.save(x)\n",
            failure(Verification(), expectation(1)),
        )
    }

    @Test
    fun testOnlyCallHasOtherArguments() {
        find(2, true)

        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "Only one matching call to $name/find happened, but arguments are not matching:\n" +
                "[0]: argument: 2, matcher: eq(1), result: -\n" +
                "[1]: argument: true, matcher: any(), result: +\n",
            failure(Verification(), expectation(1)),
        )
    }

    @Test
    fun testNoMatchingCalls() {
        find(2, true)
        find(3, true)
        save("x")

        assertEquals(
            "Verification failed: call 2 of 2: $name.find(eq(1), any()). No matching calls found.\n\n" +
                "Calls to same method:\n1) $name.find(2, true)\n2) $name.find(3, true)\n",
            failure(Verification(), SAVE to arrayOf(AnyMatcher), expectation(1)),
        )
    }

    @Test
    fun testNumberOfMatchingCalls() {
        find(1, false)
        save("x")
        find(1, true)

        verify(expectation(1), verification = Verification(exactly = 2))
        verify(expectation(1), verification = Verification(atLeast = 2, atMost = 3))
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs exactly 3 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n",
            failure(Verification(exactly = 3), expectation(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs at least 3 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n",
            failure(Verification(atLeast = 3), expectation(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs at least 0 and at most 1 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n",
            failure(Verification(atLeast = 0, atMost = 1), expectation(1)),
        )
    }

    @Test
    fun testOneMatchingCall() {
        find(1, false)

        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "One matching call found, but needs at least 2 and at most 3 calls\nCall: $name.find(1, false)",
            failure(Verification(atLeast = 2, atMost = 3), expectation(1)),
        )
    }

    @Test
    fun testCallThatShouldNotBeMade() {
        find(1, false)
        find(2, false)

        verify(expectation(3), verification = Verification(exactly = 0))
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()) should not be called\n\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.find(2, false)\n",
            failure(Verification(exactly = 0), expectation(1)),
        )
    }

    @Test
    fun testCallsThatMayNotBeMade() {
        verify(expectation(1), verification = Verification(atLeast = 0))

        find(2, false)

        verify(expectation(1), verification = Verification(atLeast = 0, atMost = 1))
    }

    @Test
    fun testInverseFailsWhenAllExpectationsMatch() {
        find(1, false)
        find(2, false)

        verify(expectation(1), expectation(3), verification = Verification(inverse = true))
        assertEquals(
            "Inverse verification failed.\n\nVerified calls:\n1) $name.find(1, false)\n2) $name.find(2, false)",
            failure(Verification(inverse = true), expectation(1), FIND to arrayOf(AnyMatcher, EqMatcher(false))),
        )
    }

    @Test
    fun testCountsAreChecked() {
        val problems = listOf(
            { Verification(atLeast = 2, exactly = 1) } to "specify either atLeast/atMost or exactly",
            { Verification(atMost = 2, exactly = 1) } to "specify either atLeast/atMost or exactly",
            { Verification(exactly = -2) } to "exactly should be positive",
            { Verification(atLeast = -1) } to "atLeast should be positive",
            { Verification(atMost = -1) } to "atMost should be positive",
            { Verification(atMost = 0) } to "atLeast should less or equal atMost",
        )

        for ((verification, message) in problems) {
            assertEquals(message, assertFailsWith<LokkiException> { verification() }.message)
        }
    }

    @Test
    fun testArgumentsAreCapturedAfterTheWholeVerification() {
        val ids = ArrayList<Int>()
        find(1, false)
        find(2, true)

        failure(Verification(), FIND to arrayOf(Matchers.capture(ids), AnyMatcher), expectation(3))
        assertEquals(emptyList(), ids)

        verify(FIND to arrayOf(Matchers.capture(ids), EqMatcher(true)))
        assertEquals(listOf(2), ids)
    }

    @Test
    fun testSlotCapturedMoreThanOnce() {
        val slot = CapturingSlot<Int>()
        find(1, false)
        find(2, false)

        val exception = assertFailsWith<LokkiException> {
            verify(FIND to arrayOf(Matchers.typed<Int>(Matchers.capture(slot)), AnyMatcher))
        }

        assertEquals(
            "$name.find(slotCapture(), any()) execution is being verified more than once and its arguments are " +
                "being captured with a slot.\nThis will store only the argument of the last invocation in the slot.\n" +
                "If you want to store all the arguments, use a mutableList to capture arguments.",
            exception.message,
        )
        assertFalse(slot.isCaptured)
    }

    private fun find(id: Int, includeDeleted: Boolean) {
        state.answer(FIND_MEMBER, arrayOf(id, includeDeleted))
    }

    private fun save(name: String) {
        state.answer(SAVE_MEMBER, arrayOf(name))
    }

    private fun expectation(id: Int): Pair<String, Array<Matcher<Any?>>> = FIND to arrayOf(EqMatcher(id), AnyMatcher)

    private fun verify(
        vararg expectations: Pair<String, Array<Matcher<Any?>>>,
        verification: Verification = Verification(),
    ) {
        expectations.forEach { (key, matchers) -> verification.expect(state, key, matchers) }
        verification.check()
    }

    private fun failure(verification: Verification, vararg expectations: Pair<String, Array<Matcher<Any?>>>): String? =
        assertFailsWith<AssertionError> { verify(*expectations, verification = verification) }.message

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val SAVE = "sample.Repo#save(kotlin.String)"
        const val EQUALS = 0
        const val HASH_CODE = 1
        const val TO_STRING = 2
        const val FIND_MEMBER = 3
        const val SAVE_MEMBER = 4
    }
}
