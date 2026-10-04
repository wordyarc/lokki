package io.lokki.internal

import io.lokki.LokkiException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MockStateTest {
    private val descriptor = MockDescriptor(
        "Repo",
        arrayOf("sample.Repo#find(kotlin.Int,kotlin.Boolean)", "sample.Repo#kotlin.String.label()|sample.Named#kotlin.String.label()"),
    )
    private val state = MockState(descriptor)

    @Test
    fun testLastMatchingStubAnswers() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"
        state.stub<String>(FIND, arrayOf(EqMatcher(1), EqMatcher(false))) returns "one"

        assertEquals("one", state.answer(0, arrayOf(1, false)))
        assertEquals("any", state.answer(0, arrayOf(2, false)))
    }

    @Test
    fun testNoAnswerWithoutMatchingStub() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"

        val exception = assertFailsWith<LokkiException> { state.answer(0, arrayOf(1, true)) }

        assertEquals("no answer found for Repo.find", exception.message)
    }

    @Test
    fun testMemberIsFoundByEveryKey() {
        state.stub<String>("sample.Named#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        assertEquals("label", state.answer(1, arrayOf("x")))
    }

    @Test
    fun testUnknownMember() {
        val exception = assertFailsWith<LokkiException> { state.stub<String>("sample.Repo#save()", emptyArray()) }

        assertEquals("Repo has no member sample.Repo#save()", exception.message)
    }

    @Test
    fun testDefaultsAreCapturedFromTheCallOfTheMember() {
        val arguments = state.captureDefaults(FIND) { state.answer(0, arrayOf(0, false)) }

        assertContentEquals(arrayOf<Any?>(0, false), arguments)
        assertFailsWith<LokkiException> { state.answer(0, arrayOf(0, false)) }
    }

    @Test
    fun testOtherMembersAnswerWhileDefaultsAreCaptured() {
        state.stub<String>("sample.Repo#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        val arguments = state.captureDefaults(FIND) { state.answer(0, arrayOf(state.answer(1, arrayOf("x")), false)) }

        assertContentEquals(arrayOf<Any?>("label", false), arguments)
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
    }
}
