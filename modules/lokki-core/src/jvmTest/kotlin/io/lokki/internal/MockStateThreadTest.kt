package io.lokki.internal

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MockStateThreadTest {
    private val state = MockState(MockDescriptor("Repo", arrayOf(FIND)))

    @Test
    fun testCallFromAnotherThreadAnswersWhileDefaultsAreCaptured() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "found"
        var answer: Any? = null

        val arguments = state.captureDefaults(FIND) {
            thread { answer = state.answer(0, arrayOf(1, true)) }.join()
            state.answer(0, arrayOf(0, false))
        }

        assertEquals("found", answer)
        assertContentEquals(arrayOf<Any?>(0, false), arguments)
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
    }
}
