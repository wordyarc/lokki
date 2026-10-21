package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.Ordering
import io.lokki.Runs
import io.lokki.just
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderedVerificationTest {
    private val mock = TestMock("repo", FIND, SAVE)
    private val state = mock.`lokki$state`
    private val name = state.name
    private val other = TestMock("other", FIND, SAVE).`lokki$state`

    init {
        for (state in listOf(state, other)) {
            state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "found"
            state.stub<Unit>(SAVE, arrayOf(AnyMatcher)) just Runs
        }
    }

    @Test
    fun testOrderedFindsCallsInOrder() {
        find(1)
        save("x")
        find(2)

        verify(Ordering.ORDERED, expectFind(1), expectFind(2))
        verify(Ordering.ORDERED, expectSave(), expectFind(2))
        assertEquals(
            "Verification failed: calls are not in verification order\n\nMatchers: \n" +
                "+$name.find(eq(2), any())\n$name.find(eq(1), any())\n\n" +
                "Calls:\n1) $name.find(1, false)\n2) $name.save(x)\n3) +$name.find(2, false)\n$CALL_SITES",
            failure(Verification(Ordering.ORDERED), expectFind(2), expectFind(1)),
        )
        assertEquals(
            "Verification failed: fewer calls happened than demanded by order verification sequence. \n\n" +
                "Matchers: \n+$name.find(eq(1), any())\n+$name.save(any())\n+$name.find(eq(2), any())\n" +
                "$name.save(any())\n\n" +
                "Calls:\n1) +$name.find(1, false)\n2) +$name.save(x)\n3) +$name.find(2, false)\n$CALL_SITES",
            failure(Verification(Ordering.ORDERED), expectFind(1), expectSave(), expectFind(2), expectSave()),
        )
    }

    @Test
    fun testSequenceCoversEveryCallOfInvolvedMocks() {
        find(1)
        other.answer(SAVE_MEMBER, arrayOf("y"))
        save("x")

        verify(Ordering.SEQUENCE, expectFind(1), expectSave())
        Verification(Ordering.SEQUENCE).apply { expect(other, SAVE, arrayOf(AnyMatcher)) }.check()
        assertEquals(
            "Verification failed: number of calls happened not matching exact number of verification sequence\n\n" +
                "Matchers: \n+$name.save(any())\n\nCalls:\n1) $name.find(1, false)\n2) +$name.save(x)\n$CALL_SITES",
            failure(Verification(Ordering.SEQUENCE), expectSave()),
        )
        assertEquals(
            "Verification failed: calls are not exactly matching verification sequence\n\n" +
                "Matchers: \n+$name.save(any())\n$name.find(eq(1), any())\n\n" +
                "Calls:\n1) $name.find(1, false)\n2) +$name.save(x)\n$CALL_SITES",
            failure(Verification(Ordering.SEQUENCE), expectSave(), expectFind(1)),
        )
    }

    @Test
    fun testAllMatchesEveryCallOfInvolvedMocks() {
        find(1)
        save("x")
        other.answer(SAVE_MEMBER, arrayOf("y"))

        verify(Ordering.ALL, expectSave(), expectFind(1))
        assertEquals(
            "Verification failed: some calls were not matched: [$name.save(x)]\n\nMatchers: \n" +
                "+$name.find(eq(1), any())\n\nCalls:\n1) +$name.find(1, false)\n2) $name.save(x)\n$CALL_SITES",
            failure(Verification(Ordering.ALL), expectFind(1)),
        )
        assertEquals(
            "Verification failed: call 1 of 1: $name.find(eq(2), any()). Only one matching call to $name/find " +
                "happened, but arguments are not matching:\n[0]: argument: 1, matcher: eq(2), result: -\n" +
                "[1]: argument: false, matcher: any(), result: +\n$CALL_SITES",
            failure(Verification(Ordering.ALL), expectFind(2)),
        )
    }

    @Test
    fun testInverseReportsMatchedCallsInOrder() {
        find(1)
        find(2)

        val inverse = Verification(Ordering.ORDERED, inverse = true)
        verify(Ordering.ORDERED, expectFind(2), expectFind(1), verification = inverse)
        assertEquals(
            "Inverse verification failed.\n\nVerified calls:\n1) $name.find(1, false)\n2) $name.find(2, false)",
            failure(Verification(Ordering.ORDERED, inverse = true), expectFind(1), expectFind(2)),
        )
        assertFailsWith<AssertionError> { state.confirmVerified() }
    }

    @Test
    fun testLinksAreExpectationsOfOrderedVerification() {
        find(1)
        other.answer(SAVE_MEMBER, arrayOf("y"))

        for (ordering in listOf(Ordering.ALL, Ordering.ORDERED, Ordering.SEQUENCE)) {
            verifyLink(Verification(ordering))
            assertEquals(
                "Inverse verification failed.\n\nVerified calls:\n1) $name.find(1, false)\n2) ${other.name}.save(y)",
                assertFailsWith<AssertionError> { verifyLink(Verification(ordering, inverse = true)) }.message,
            )
        }
        state.confirmVerified()
        other.confirmVerified()
    }

    @Test
    fun testCountsAreOnlyAllowedInUnorderedVerification() {
        for (ordering in listOf(Ordering.ALL, Ordering.ORDERED, Ordering.SEQUENCE)) {
            assertEquals(
                "atLeast, atMost, exactly is only allowed in unordered verify block",
                assertFailsWith<LokkiException> { Verification(ordering, exactly = 1) }.message,
            )
        }
        Verification(Ordering.ORDERED, atLeast = 1, atMost = Int.MAX_VALUE, exactly = -1)
    }

    @Test
    fun testMatchersAreCalledOncePerCall() {
        var first = 0
        var second = 0
        val counted = Matchers.named("first()") { first++; true }
        val one = Matchers.named("one()") { second++; it == 1 }
        find(1)
        find(2)

        assertEquals(
            "Verification failed: calls are not exactly matching verification sequence\n\nMatchers: \n" +
                "+$name.find(first(), any())\n$name.find(one(), any())\n\n" +
                "Calls:\n1) $name.find(1, false)\n2) +$name.find(2, false)\n$CALL_SITES",
            failure(
                Verification(Ordering.SEQUENCE),
                FIND to arrayOf(counted, AnyMatcher),
                FIND to arrayOf(one, AnyMatcher),
            ),
        )
        assertEquals(2, first)
        assertEquals(2, second)
    }

    @Test
    fun testArgumentsAreCapturedInOrderOfCalls() {
        val ids = mutableListOf<Int>()
        val capture = Matchers.capture(ids)
        find(1)
        find(2)
        find(3)

        val expectation: Pair<String, Array<Matcher<Any?>>> = FIND to arrayOf(capture, AnyMatcher)
        verify(Ordering.ORDERED, expectation, expectation, expectation)
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun testOnlyMocksWithoutCallsPass() {
        find(1)
        val idle = TestMock(null, FIND)

        for (ordering in Ordering.entries) {
            Verification(ordering).apply { expectNoCalls(idle) }.check()
        }
    }

    @Test
    fun testMatchedCallsAreMarkedVerified() {
        find(1)
        find(2)
        find(1)

        verify(Ordering.ORDERED, expectFind(1), expectFind(1))
        assertEquals(
            "Verification acknowledgment failed\n\nVerified call count: 2\nRecorded call count: 3\n\n\n" +
                "Not verified calls:\n1) $name.find(2, false)\n$CALL_SITES",
            assertFailsWith<AssertionError> { state.confirmVerified() }.message,
        )
        verify(Ordering.SEQUENCE, expectFind(1), expectFind(2), expectFind(1))
        state.confirmVerified()
    }

    private fun verifyLink(verification: Verification) {
        verification.expectLink(mock, FIND) { arrayOf(EqMatcher(1), EqMatcher(false)) }
        verification.expect(other, SAVE, arrayOf(AnyMatcher))
        verification.check()
    }

    private fun find(id: Int) {
        state.answer(FIND_MEMBER, arrayOf(id, false))
    }

    private fun save(name: String) {
        state.answer(SAVE_MEMBER, arrayOf(name))
    }

    private fun expectFind(id: Int): Pair<String, Array<Matcher<Any?>>> = FIND to arrayOf(EqMatcher(id), AnyMatcher)

    private fun expectSave(): Pair<String, Array<Matcher<Any?>>> = SAVE to arrayOf(AnyMatcher)

    private fun verify(
        ordering: Ordering,
        vararg expectations: Pair<String, Array<Matcher<Any?>>>,
        verification: Verification = Verification(ordering),
    ) {
        expectations.forEach { (key, matchers) -> verification.expect(state, key, matchers) }
        verification.check()
    }

    private fun failure(verification: Verification, vararg expectations: Pair<String, Array<Matcher<Any?>>>): String? =
        assertFailsWith<AssertionError> {
            expectations.forEach { (key, matchers) -> verification.expect(state, key, matchers) }
            verification.check()
        }.message

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val SAVE = "sample.Repo#save(kotlin.String)"
        const val FIND_MEMBER = 3
        const val SAVE_MEMBER = 4
        const val CALL_SITES = "\nCall sites are not recorded; rerun with -Dlokki.recordStackTraces=true"
    }
}
