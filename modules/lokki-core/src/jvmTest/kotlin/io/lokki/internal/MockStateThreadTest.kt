package io.lokki.internal

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MockStateThreadTest {
    private val state = MockState(Any(), MockSpec(null, 0), MockDescriptor("Repo", ANY_KEYS + FIND))

    @Test
    fun testCallFromAnotherThreadAnswersWhileDefaultsAreCaptured() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "found"
        var answer: Any? = null

        val arguments = state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) {
            thread { answer = state.answer(FIND_MEMBER, arrayOf(1, true)) }.join()
            state.answer(FIND_MEMBER, arrayOf(0, false))
        }

        assertEquals("found", answer)
        assertContentEquals(arrayOf<Any?>(0, false), arguments)
    }

    @Test
    fun testRegistrationOfAnotherThreadDoesNotHoldState() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)).returnsMany("a", "b")
        var answers: List<Any?> = emptyList()

        GeneratedMock.withoutStateChanges {
            thread { answers = List(2) { state.answer(FIND_MEMBER, arrayOf(1, true)) } }.join()
        }

        assertEquals(listOf<Any?>("a", "b"), answers)
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val FIND_MEMBER = 3
    }
}
