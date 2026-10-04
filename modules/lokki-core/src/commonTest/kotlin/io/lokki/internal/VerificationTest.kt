package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.Runs
import io.lokki.just
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class VerificationTest {
    private val mock = TestMock("repo", FIND, SAVE, COUNT)
    private val state = mock.`lokki$state`
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
        val other = MockState(Any(), MockSpec(null, 0), state.descriptor)
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
                "Calls to same mock:\n1) $name.save(x)\n$CALL_SITES",
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
                "[1]: argument: true, matcher: any(), result: +\n$CALL_SITES",
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
                "Calls to same method:\n1) $name.find(2, true)\n2) $name.find(3, true)\n$CALL_SITES",
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
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n$CALL_SITES",
            failure(Verification(exactly = 3), expectation(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs at least 3 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n$CALL_SITES",
            failure(Verification(atLeast = 3), expectation(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs at least 0 and at most 1 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) $name.find(1, true)\n$CALL_SITES",
            failure(Verification(atLeast = 0, atMost = 1), expectation(1)),
        )
    }

    @Test
    fun testOneMatchingCall() {
        find(1, false)

        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "One matching call found, but needs at least 2 and at most 3 calls\n" +
                "Call: $name.find(1, false)$CALL_SITES",
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
                "Calls:\n1) $name.find(1, false)\n2) $name.find(2, false)\n$CALL_SITES",
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

    @Test
    fun testPredicateIsCalledOncePerCall() {
        var calls = 0
        val counting = Matchers.named("counting()") { calls++; it == 1 }
        find(2, true)

        val message = failure(Verification(), FIND to arrayOf(counting, AnyMatcher))
        assertEquals(1, calls)
        assertContains(message!!, "[0]: argument: 2, matcher: counting(), result: -\n")

        find(1, false)
        verify(FIND to arrayOf(counting, AnyMatcher))
        assertEquals(3, calls)
    }

    @Test
    fun testLinkIsExpectedAtLeastOnce() {
        save("x")

        assertEquals(
            "Verification failed: call 1 of 2: $name.find(eq(1), eq(false)) was not called.\n\n" +
                "Calls to same mock:\n1) $name.save(x)\n$CALL_SITES",
            assertFailsWith<AssertionError> { verifyLink(Verification(exactly = 1)) }.message,
        )

        find(1, false)
        find(1, false)
        verifyLink(Verification(exactly = 1))
        state.confirmVerified()
    }

    @Test
    fun testLinkIsOnlyEvaluatedWithoutLowerBoundOrInverse() {
        save("x")

        val verifications = listOf(Verification(exactly = 0), Verification(atLeast = 0), Verification(inverse = true))
        for (verification in verifications) {
            verification.expectLink(mock, FIND) { fail("matchers of a link that is not expected") }
            verification.expect(state, FIND, arrayOf(EqMatcher(5), AnyMatcher))
            verification.check()
        }
    }

    @Test
    fun testLinkOnObjectThatIsNotMockIsOnlyEvaluated() {
        save("x")
        val verification = Verification()

        verification.expectLink("not a mock", FIND) { fail("matchers of a link that is not expected") }
        verification.expect(state, SAVE, arrayOf(AnyMatcher))
        verification.check()
    }

    @Test
    fun testMatchedCallsAreMarkedVerified() {
        find(1, false)
        save("x")
        verify(expectation(1))

        assertEquals(
            "Verification acknowledgment failed\n\nVerified call count: 1\nRecorded call count: 2\n\n\n" +
                "Not verified calls:\n1) $name.save(x)\n$CALL_SITES",
            assertFailsWith<AssertionError> { state.confirmVerified() }.message,
        )
        failure(Verification(), SAVE to arrayOf(AnyMatcher), expectation(3))
        verify(SAVE to arrayOf(AnyMatcher), expectation(3), verification = Verification(inverse = true))
        assertFailsWith<AssertionError> { state.confirmVerified() }

        verify(SAVE to arrayOf(AnyMatcher))
        state.confirmVerified()
    }

    @Test
    fun testCallsWithoutAnswerAreReportedFirst() {
        assertFailsWith<LokkiException> { state.answer(COUNT_MEMBER, emptyArray()) }
        save("x")
        verify(SAVE to arrayOf(AnyMatcher))

        val unanswered = "Calls without an answer threw LokkiException:\n1) $name.count()\n\n"
        assertEquals(
            unanswered + "Verification failed: call 1 of 1: $name.find(eq(1), any()) was not called.\n\n" +
                "Calls to same mock:\n1) $name.count()\n2) $name.save(x)\n$CALL_SITES",
            failure(Verification(), expectation(1)),
        )
        assertEquals(
            unanswered + "Verification acknowledgment failed\n\nVerified call count: 1\nRecorded call count: 2\n\n\n" +
                "Not verified calls:\n1) $name.count()\n$CALL_SITES",
            assertFailsWith<AssertionError> { state.confirmVerified() }.message,
        )
    }

    @Test
    fun testMocksWithoutCallsPass() {
        val other = TestMock("other", FIND)
        val verification = Verification()

        verification.expectNoCalls(mock)
        verification.expectNoCalls(listOf(other, other))
        verification.check()
    }

    @Test
    fun testCalledMocksAreReported() {
        val other = TestMock("other", FIND)
        val otherName = other.`lokki$state`.name
        find(1, false)
        assertFailsWith<LokkiException> { other.`lokki$state`.answer(FIND_MEMBER, arrayOf(2, true)) }
        val verification = Verification()

        verification.expectNoCalls(listOf(mock, other, mock))
        assertEquals(
            "Calls without an answer threw LokkiException:\n1) $otherName.find(2, true)\n\n" +
                "Verification failed: $name, $otherName should not be called:\n" +
                "1) $name.find(1, false)\n2) $otherName.find(2, true)\n$CALL_SITES",
            assertFailsWith<AssertionError> { verification.check() }.message,
        )
    }

    @Test
    fun testCallsAreCheckedBeforeMocksWithoutCalls() {
        find(1, false)
        val verification = Verification()

        verification.expectNoCalls(mock)
        verification.expect(state, FIND, arrayOf(EqMatcher(2), AnyMatcher))
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(2), any()). " +
                "Only one matching call to $name/find happened, but arguments are not matching:\n" +
                "[0]: argument: 1, matcher: eq(2), result: -\n" +
                "[1]: argument: false, matcher: any(), result: +\n$CALL_SITES",
            assertFailsWith<AssertionError> { verification.check() }.message,
        )
    }

    @Test
    fun testInverseCoversMocksWithoutCalls() {
        find(1, false)
        val called = Verification(inverse = true)
        val uncalled = Verification(inverse = true)

        called.expectNoCalls(mock)
        called.check()
        uncalled.expectNoCalls(TestMock(null, FIND))
        assertEquals(
            "Inverse verification failed.\n\nVerified calls:\n",
            assertFailsWith<AssertionError> { uncalled.check() }.message,
        )
    }

    @Test
    fun testMocksWithoutCallsMustBeMocks() {
        val verification = Verification()

        assertEquals(
            "text is not a lokki mock",
            assertFailsWith<LokkiException> { verification.expectNoCalls("text") }.message,
        )
        assertEquals(
            "text is not a lokki mock",
            assertFailsWith<LokkiException> { verification.expectNoCalls(listOf(mock, "text")) }.message,
        )
    }

    @Test
    fun testCallCounts() {
        find(1, false)
        find(1, true)

        verify(expectation(1), verification = Verification(2))
        verify(expectation(1), verification = Verification(1..2))
        verify(expectation(3), verification = Verification(0))
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs exactly 3 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.find(1, true)\n$CALL_SITES",
            failure(Verification(3), expectation(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(1), any()). " +
                "2 matching calls found, but needs at least 3 and at most 4 calls\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.find(1, true)\n$CALL_SITES",
            failure(Verification(3..4), expectation(1)),
        )
        assertEquals("atLeast should be positive", assertFailsWith<LokkiException> { Verification(-1) }.message)
        assertEquals(
            "atLeast should less or equal atMost",
            assertFailsWith<LokkiException> { Verification(2..1) }.message,
        )
    }

    private fun verifyLink(verification: Verification) {
        verification.expectLink(mock, FIND) { arrayOf(EqMatcher(1), EqMatcher(false)) }
        verification.expect(state, SAVE, arrayOf(AnyMatcher))
        verification.check()
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
        const val COUNT = "sample.Repo#count()"
        const val EQUALS = 0
        const val HASH_CODE = 1
        const val TO_STRING = 2
        const val FIND_MEMBER = 3
        const val SAVE_MEMBER = 4
        const val COUNT_MEMBER = 5
        const val CALL_SITES = "\nCall sites are not recorded; rerun with -Dlokki.recordStackTraces=true"
    }
}
