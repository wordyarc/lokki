package io.lokki.internal

import io.lokki.Matcher
import io.lokki.Ordering
import io.lokki.Runs
import io.lokki.just
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VerificationTimeoutTest {
    private val state = TestMock("bell", RING).`lokki$state`
    private val name = state.name

    init {
        state.stub<Unit>(RING, arrayOf(AnyMatcher)) just Runs
    }

    @Test
    fun testVerificationWaitsForCalls() {
        later(50) { ring(1) }

        val start = System.nanoTime()
        verify(Verification(timeout = 5_000), expectRing(1))
        assertTrue(System.nanoTime() - start < 4_000_000_000, "waited for the whole timeout")
    }

    @Test
    fun testOrderedVerificationWaitsForCalls() {
        later(50) {
            ring(1)
            ring(2)
        }

        verify(Verification(Ordering.SEQUENCE, timeout = 5_000), expectRing(1), expectRing(2))
    }

    @Test
    fun testMatchersAreCalledOncePerCallAcrossChecks() {
        var calls = 0
        val one = Matchers.named("one()") { calls++; it == 1 }
        later(20) {
            ring(2)
            Thread.sleep(20)
            ring(3)
            Thread.sleep(20)
            ring(1)
        }

        verify(Verification(timeout = 5_000), RING to arrayOf(one))
        assertEquals(3, calls)
    }

    @Test
    fun testFailureMentionsTimeout() {
        assertEquals(
            "Verification failed: call 1 of 1: $name.ring(eq(1)) was not called (timeout = 50 ms)",
            failure(Verification(timeout = 50), expectRing(1)),
        )
    }

    @Test
    fun testUpperBoundIsCheckedAfterTimeout() {
        later(20) {
            ring(1)
            Thread.sleep(30)
            ring(1)
        }

        val message = failure(Verification(exactly = 1, timeout = 300), expectRing(1))
        assertTrue(message!!.startsWith("Verification failed: call 1 of 1: $name.ring(eq(1)). 2 matching calls found"))
        assertTrue(message.endsWith(" (timeout = 300 ms)"))
    }

    @Test
    fun testMocksWithoutCallsAreCheckedAfterTimeout() {
        later(30) { ring(1) }

        val verification = Verification(timeout = 300)
        verification.expectNoCalls(TestMock(null, RING))
        verification.expect(state, RING, arrayOf(EqMatcher(1)))
        verification.check()

        val silent = TestMock(null, RING)
        val silentState = silent.`lokki$state`
        silentState.stub<Unit>(RING, arrayOf(AnyMatcher)) just Runs
        later(30) { silentState.answer(RING_MEMBER, arrayOf(1)) }
        assertFailsWith<AssertionError> {
            Verification(timeout = 300).apply { expectNoCalls(silent) }.check()
        }
    }

    @Test
    fun testVerificationWaitsForCallOfMockThatLinkReturnsLater() {
        val house = TestMock("house", RING)
        house.`lokki$state`.stub<Any>(RING, arrayOf(AnyMatcher)) answers {
            TestMock("door", RING).also { it.`lokki$state`.stub<Unit>(RING, arrayOf(AnyMatcher)) just Runs }
        }
        later(50) {
            val door = house.`lokki$state`.answer(RING_MEMBER, arrayOf(1)) as TestMock
            Thread.sleep(50)
            door.`lokki$state`.answer(RING_MEMBER, arrayOf(2))
        }

        val start = System.nanoTime()
        val verification = Verification(timeout = 5_000)
        GeneratedMock.withoutStateChanges {
            verification.expectLink(house, RING) { arrayOf(EqMatcher(1)) }
            val door = house.`lokki$state`.answer(RING_MEMBER, arrayOf(1)) as TestMock
            verification.expect(door.`lokki$state`, RING, arrayOf(EqMatcher(2)))
            verification.check()
        }
        assertTrue(System.nanoTime() - start < 4_000_000_000, "waited for the whole timeout")
    }

    private fun later(millis: Long, block: () -> Unit) {
        thread {
            Thread.sleep(millis)
            block()
        }
    }

    private fun ring(id: Int) {
        state.answer(RING_MEMBER, arrayOf(id))
    }

    private fun expectRing(id: Int): Pair<String, Array<Matcher<Any?>>> = RING to arrayOf(EqMatcher(id))

    private fun verify(verification: Verification, vararg expectations: Pair<String, Array<Matcher<Any?>>>) {
        expectations.forEach { (key, matchers) -> verification.expect(state, key, matchers) }
        verification.check()
    }

    private fun failure(verification: Verification, vararg expectations: Pair<String, Array<Matcher<Any?>>>): String? =
        assertFailsWith<AssertionError> { verify(verification, *expectations) }.message

    private companion object {
        const val RING = "sample.Bell#ring(kotlin.Int)"
        const val RING_MEMBER = 3
    }
}
