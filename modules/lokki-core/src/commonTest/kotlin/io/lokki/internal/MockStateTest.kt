package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MockStateTest {
    private val descriptor = MockDescriptor(
        "Repo",
        ANY_KEYS + arrayOf(FIND, "sample.Repo#kotlin.String.label()", "sample.Repo#<get-isOpen>()"),
        arrayOf("sample.Named#kotlin.String.label()", "sample.Repo#kotlin.String.label()"),
    )
    private val owner = Any()
    private val state = MockState(owner, MockSpec(null, 0), descriptor)
    private val name = state.answer(TO_STRING, emptyArray())

    @Test
    fun testLastMatchingStubAnswers() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"
        state.stub<String>(FIND, arrayOf(EqMatcher(1), EqMatcher(false))) returns "one"

        assertEquals("one", state.answer(FIND_MEMBER, arrayOf(1, false)))
        assertEquals("any", state.answer(FIND_MEMBER, arrayOf(2, false)))
    }

    @Test
    fun testNoAnswerWithoutMatchingStub() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"

        state.stub<String>("sample.Repo#kotlin.String.label()", arrayOf(Matchers.more(5))) returns "label"

        val exception = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }

        assertEquals(
            "no answer found for $name.find(1, true) among the configured answers: " +
                "($name.find(any(), eq(false))\n$name.label(more(5)))",
            exception.message,
        )
    }

    @Test
    fun testNoAnswerOfAccessorIsReportedByItsJvmName() {
        val exception = assertFailsWith<LokkiException> { state.answer(OPEN_MEMBER, emptyArray()) }

        assertEquals("no answer found for $name.isOpen() among the configured answers: ()", exception.message)
    }

    @Test
    fun testMethodsOfAnyHaveIdentityAnswers() {
        assertEquals(true, state.answer(EQUALS, arrayOf(owner)))
        assertEquals(false, state.answer(EQUALS, arrayOf(Any())))
        assertEquals(false, state.answer(EQUALS, arrayOf(null)))
        assertEquals(owner.hashCode(), state.answer(HASH_CODE, emptyArray()))
        assertTrue(Regex("""Repo\(#\d+\)""").matches(name as String))
    }

    @Test
    fun testMethodsOfAnyAreStubbed() {
        state.stub<String>("kotlin.Any#toString()", emptyArray()) returns "repo"
        state.stub<Boolean>("kotlin.Any#equals(kotlin.Any?)", arrayOf(EqMatcher("other"))) returns true

        assertEquals("repo", state.answer(TO_STRING, emptyArray()))
        assertEquals(true, state.answer(EQUALS, arrayOf("other")))
        assertEquals(false, state.answer(EQUALS, arrayOf("another")))
        val exception = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
        assertTrue(exception.message!!.startsWith("no answer found for $name.find(1, true)"))
    }

    @Test
    fun testNameOfMockPrecedesItsNumber() {
        val named = MockState(owner, MockSpec("repo", 0), descriptor)

        assertTrue(Regex("""Repo\(repo#\d+\)""").matches(named.answer(TO_STRING, emptyArray()) as String))
    }

    @Test
    fun testMemberIsFoundByEveryKey() {
        state.stub<String>("sample.Named#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        assertEquals("label", state.answer(LABEL_MEMBER, arrayOf("x")))
    }

    @Test
    fun testKeyOfTwoMembersIsRejected() {
        val state = MockState(owner, MockSpec(null, 0), MockDescriptor("Repo", ANY_KEYS + arrayOf(FIND, FIND)))

        val exception = assertFailsWith<LokkiException> { state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) }

        assertEquals("Repo has two members with the key $FIND", exception.message)
    }

    @Test
    fun testUnknownMember() {
        val exception = assertFailsWith<LokkiException> { state.stub<String>("sample.Repo#save()", emptyArray()) }

        assertEquals("Repo has no member sample.Repo#save()", exception.message)
    }

    @Test
    fun testDefaultsAreCapturedFromTheCallOfTheMember() {
        val arguments = state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) {
            state.answer(FIND_MEMBER, arrayOf(0, false))
        }

        assertContentEquals(arrayOf<Any?>(0, false), arguments)
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(0, false)) }
    }

    @Test
    fun testOtherMembersAnswerWhileDefaultsAreCaptured() {
        state.stub<String>("sample.Repo#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        val arguments = state.captureDefaults(FIND, arrayOf("id", null)) {
            state.answer(FIND_MEMBER, arrayOf(state.answer(LABEL_MEMBER, arrayOf("x")), false))
        }

        assertContentEquals(arrayOf<Any?>("label", false), arguments)
    }

    @Test
    fun testDefaultThatDependsOnPlaceholderIsUnstable() {
        val exception = assertFailsWith<LokkiException> {
            state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { alternate ->
                state.answer(FIND_MEMBER, arrayOf(if (alternate) 1 else 0, alternate))
            }
        }

        assertEquals(
            "default value of 'includeDeleted' is unstable, has no equals, or depends on a matcher argument; " +
                "pass it explicitly or use any()",
            exception.message,
        )
    }

    @Test
    fun testPlaceholdersOfPassedArgumentsMayDiffer() {
        val arguments = state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { alternate ->
            state.answer(FIND_MEMBER, arrayOf(if (alternate) 1 else 0, true))
        }

        assertContentEquals(arrayOf<Any?>(0, true), arguments)
    }

    @Test
    fun testFailureOfDefaultValueIsReported() {
        val failure = IllegalStateException("no clock")

        val exception = assertFailsWith<LokkiException> {
            state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { throw failure }
        }

        assertEquals(
            "default values of Repo.find cannot be evaluated; pass them explicitly or use any()",
            exception.message,
        )
        assertSame(failure, exception.cause)
    }

    @Test
    fun testAnswersFollowOneAnotherAndTheLastRepeats() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a" andThen "b" andThenMany listOf("c", "d")

        assertEquals(listOf<Any?>("a", "b", "c", "d", "d"), List(5) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testAnswerAddedLaterFollowsTheRepeatedOne() {
        val stub = state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a"
        state.answer(FIND_MEMBER, arrayOf(1, true))
        state.answer(FIND_MEMBER, arrayOf(1, true))
        stub andThen "b"

        assertEquals(listOf<Any?>("a", "b", "b"), List(3) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testManyAnswersAndExceptions() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher))
            .returnsMany("a", "b") andThenThrows first andThenThrowsMany listOf(second)

        assertEquals(listOf<Any?>("a", "b"), List(2) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(first, assertFailsWith<IllegalStateException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(second, assertFailsWith<IllegalArgumentException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(second, assertFailsWith<IllegalArgumentException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testStubNeedsAnAnswer() {
        val stub = state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher))

        assertEquals("a stub needs at least one answer", assertFailsWith<LokkiException> { stub returnsMany emptyList() }.message)
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
    }

    @Test
    fun testAnswerReceivesTheCall() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { call ->
            "${call.invocation.self === owner} $nArgs ${firstArg<Int>() + 1} ${secondArg<Boolean>()} ${lastArg<Boolean>()} ${arg<Int>(0)} $args"
        }

        assertEquals("true 2 8 true true 7 [7, true]", state.answer(FIND_MEMBER, arrayOf(7, true)))
    }

    @Test
    fun testArgumentIsReturned() {
        state.stub<Any?>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returnsArgument 1

        assertEquals(true, state.answer(FIND_MEMBER, arrayOf(7, true)))
    }

    @Test
    fun testArgumentOutOfRangeFailsAtRegistration() {
        for (index in listOf(-1, 2)) {
            val stub = state.stub<Any?>(FIND, arrayOf(AnyMatcher, AnyMatcher))

            assertEquals(
                "returnsArgument($index) is out of range: the number of arguments of Repo.find is 2",
                assertFailsWith<LokkiException> { stub returnsArgument index }.message,
            )
        }
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
    }

    @Test
    fun testArgumentsAreCapturedAfterTheWholeCallMatched() {
        val ids = ArrayList<Int>()
        val flag = CapturingSlot<Boolean>()
        state.stub<String>(FIND, arrayOf(Matchers.capture(ids), EqMatcher(true))) returns "a"
        state.stub<String>(FIND, arrayOf(EqMatcher(5), Matchers.capture(flag))) returns "b"

        assertEquals("a", state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(2, false)) }
        assertEquals("b", state.answer(FIND_MEMBER, arrayOf(5, true)))

        assertEquals(listOf(1), ids)
        assertEquals(true, flag.captured)
    }

    @Test
    fun testRegistrationDoesNotChangeState() {
        val ids = ArrayList<Int>()
        state.stub<String>(FIND, arrayOf(Matchers.capture(ids), AnyMatcher)).returnsMany("a", "b")

        val answers = GeneratedMock.withoutStateChanges { List(2) { state.answer(FIND_MEMBER, arrayOf(1, true)) } }

        assertEquals(listOf<Any?>("a", "a"), answers)
        assertEquals(emptyList(), ids)
        assertEquals(listOf<Any?>("a", "b"), List(2) { state.answer(FIND_MEMBER, arrayOf(2, true)) })
        assertEquals(listOf(2, 2), ids)
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val EQUALS = 0
        const val HASH_CODE = 1
        const val TO_STRING = 2
        const val FIND_MEMBER = 3
        const val LABEL_MEMBER = 4
        const val OPEN_MEMBER = 5
    }
}
