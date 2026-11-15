package io.lokki.internal

import io.lokki.Ordering
import io.lokki.Runs
import io.lokki.just
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.fail

class LinkVerificationTest {
    private val house = TestMock("house", DOOR)
    private val first = door("first")
    private val second = door("second")

    @Test
    fun testCallIsVerifiedOnMockThatLinkReturnedToCode() {
        house.stub().returnsMany(first, second)
        open(enter(house))

        verifyOpened(Verification())
        verifyOpened(Verification(exactly = 1))
        assertEquals(
            "Verification failed: call 1 of 1: ${second.name}.open() should not be called\n\n" +
                "Calls:\n1) ${first.name}.open()\n$CALL_SITES",
            failure(Verification(exactly = 0)),
        )
        assertEquals(
            "Inverse verification failed.\n\nVerified calls:\n1) ${first.name}.open()",
            failure(Verification(inverse = true)),
        )
        first.`lokki$state`.confirmVerified()
    }

    @Test
    fun testCallsAreCountedOverEveryMockThatLinkReturned() {
        house.stub().returnsMany(first, second)
        open(enter(house))
        open(enter(house))

        verifyOpened(Verification(exactly = 2))
        assertEquals(
            "Verification failed: call 2 of 2: ${second.name}.open(). 2 matching calls found, " +
                "but needs exactly 1 calls\nCalls:\n1) ${first.name}.open()\n2) ${second.name}.open()\n$CALL_SITES",
            failure(Verification(exactly = 1)),
        )
    }

    @Test
    fun testOnlyCallsThatMatchLinkLeadToItsMocks() {
        house.stub().returnsMany(first, second)
        open(enter(house, id = 1))

        verifyOpened(Verification(exactly = 0), id = 2)
        assertEquals(
            "Verification failed: call 1 of 2: ${house.name}.door(eq(2)). Only one matching call to " +
                "${house.name}/door happened, but arguments are not matching:\n" +
                "[0]: argument: 1, matcher: eq(2), result: -\n$CALL_SITES",
            failure(Verification(), id = 2),
        )
    }

    @Test
    fun testMockThatLinkAnswersWithNowIsVerifiedWithoutCallsOfLink() {
        house.stub().returnsMany(first, second)
        open(first)

        assertEquals(
            "Verification failed: call 1 of 1: ${first.name}.open() should not be called\n\n" +
                "Calls:\n1) ${first.name}.open()\n$CALL_SITES",
            failure(Verification(exactly = 0)),
        )
        assertEquals(
            "Verification failed: call 1 of 2: ${house.name}.door(eq(1)) was not called",
            failure(Verification()),
        )
    }

    @Test
    fun testMatchersOfLinkWithoutCallsAreNotBuilt() {
        house.stub().returns(first)
        val verification = Verification(exactly = 0)

        verification.expectLink(house, DOOR) { fail("matchers of a link that was not called") }
        verification.expect(first.`lokki$state`, OPEN, emptyArray())
        verification.check()
    }

    @Test
    fun testOrderedVerificationFollowsLink() {
        house.stub().returnsMany(first, second)
        open(enter(house))

        for (ordering in listOf(Ordering.ALL, Ordering.ORDERED, Ordering.SEQUENCE)) verifyOpened(Verification(ordering))
        open(enter(house))
        assertEquals(
            "Verification failed: number of calls happened not matching exact number of verification sequence\n\n" +
                "Matchers: \n+${house.name}.door(eq(1))\n+${second.name}.open()\n\n" +
                "Calls:\n1) ${house.name}.door(1)\n2) ${first.name}.open()\n3) +${house.name}.door(1)\n" +
                "4) +${second.name}.open()\n$CALL_SITES",
            failure(Verification(Ordering.SEQUENCE)),
        )
    }

    @Test
    fun testEveryLinkOfChainIsFollowed() {
        val street = TestMock("street", HOUSE)
        val other = TestMock("other", DOOR)
        street.`lokki$state`.stub<Any>(HOUSE, emptyArray()).returnsMany(house, other)
        house.stub().returns(first)
        other.stub().returns(second)
        open(enter(street.`lokki$state`.answer(MEMBER, emptyArray()) as TestMock))

        fun verifyChain(verification: Verification) {
            GeneratedMock.withoutStateChanges {
                verification.expectLink(street, HOUSE) { emptyArray() }
                val current = street.`lokki$state`.answer(MEMBER, emptyArray()) as TestMock
                verification.expectLink(current, DOOR) { arrayOf(EqMatcher(1)) }
                verification.expect(enter(current).`lokki$state`, OPEN, emptyArray())
                verification.check()
            }
        }

        verifyChain(Verification())
        assertEquals(
            "Verification failed: call 1 of 1: ${second.name}.open() should not be called\n\n" +
                "Calls:\n1) ${first.name}.open()\n$CALL_SITES",
            assertFailsWith<AssertionError> { verifyChain(Verification(exactly = 0)) }.message,
        )
    }

    @Test
    fun testMocksOfComputedAnswersAreFollowed() {
        val doors = ArrayList<TestMock>()
        house.stub() answers { door("answered").also { doors += it } }
        open(enter(house))

        verifyOpened(Verification())
        assertFailsWith<AssertionError> { verifyOpened(Verification(exactly = 0)) }
        assertEquals(3, doors.size)
    }

    @Test
    fun testMocksOfArgumentsOriginalsAndRelaxedChildrenAreFollowed() {
        val echo = TestMock("echo", DOOR)
        echo.stub() returnsArgument 0
        assertSame(first, echo.`lokki$state`.answer(MEMBER, arrayOf(first)))
        val spy = TestMock("spy", DOOR, flags = SPY)
        spy.original = { _, _ -> first }
        enter(spy)
        val relaxed = TestMock("relaxed", DOOR, flags = RELAXED)
        relaxed.relaxed = { ChildMock { second } }
        enter(relaxed)

        assertEquals(
            listOf(first, first, second).map { it.`lokki$state` },
            listOf(echo, spy, relaxed).map { it.`lokki$state`.records().single().result },
        )
    }

    @Test
    fun testMocksOfSuspendingAnswersAreFollowed() {
        val suspending = TestMock("suspending", DOOR, suspending = setOf(DOOR))
        var pending: Continuation<Unit>? = null
        suspending.stub() coAnswers {
            suspendCoroutine { pending = it }
            first
        }
        var returned: Any? = null

        suspend { suspending.`lokki$state`.coAnswer<Any>(MEMBER, arrayOf(1)) }
            .startCoroutine(Continuation(EmptyCoroutineContext) { returned = it.getOrThrow() })
        val record = suspending.`lokki$state`.records().single()
        assertEquals(null, record.result)
        pending!!.resume(Unit)

        assertSame(first, returned)
        assertSame(first.`lokki$state`, record.result)
    }

    private fun door(name: String): TestMock =
        TestMock(name, OPEN, units = setOf(OPEN)).also { it.`lokki$state`.stub<Unit>(OPEN, emptyArray()) just Runs }

    private fun TestMock.stub() = `lokki$state`.stub<Any>(DOOR, arrayOf(AnyMatcher))

    private fun enter(house: TestMock, id: Int = 1): TestMock = house.`lokki$state`.answer(MEMBER, arrayOf(id)) as TestMock

    private fun open(door: TestMock) {
        door.`lokki$state`.answer(MEMBER, emptyArray())
    }

    private fun verifyOpened(verification: Verification, id: Int = 1) {
        GeneratedMock.withoutStateChanges {
            verification.expectLink(house, DOOR) { arrayOf(EqMatcher(id)) }
            verification.expect(enter(house, id).`lokki$state`, OPEN, emptyArray())
            verification.check()
        }
    }

    private fun failure(verification: Verification, id: Int = 1): String? =
        assertFailsWith<AssertionError> { verifyOpened(verification, id) }.message

    private val TestMock.name: String
        get() = `lokki$state`.name

    private companion object {
        const val HOUSE = "sample.Street#house()"
        const val DOOR = "sample.House#door(kotlin.Int)"
        const val OPEN = "sample.Door#open()"
        const val MEMBER = 3
        const val CALL_SITES = "\nCall sites are not recorded; rerun with -Dlokki.recordStackTraces=true"
    }
}
