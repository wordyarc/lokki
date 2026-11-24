package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Matcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChildMocksTest {
    private val parent = TestMock(null, FIND)
    private val state = parent.`lokki$state`
    private val specs = ArrayList<MockSpec>()
    private val factory: (MockSpec) -> Any = { spec ->
        specs += spec
        TestMock(spec.name, FIND, flags = spec.flags)
    }

    @Test
    fun testLinkWithoutAnswerStubsChild() {
        val child = link(stubs = true)

        assertSame(child, find(1))
        assertSame(child, link(stubs = true))
        assertFailsWith<LokkiException> { find(2) }
        assertEquals("Repo(child of ${state.label()}#${child.id()})", child.name())
        assertEquals(1, specs.size)
    }

    @Test
    fun testLinkWithoutStubsCachesChild() {
        val child = link(stubs = false)

        assertFailsWith<LokkiException> { find(1) }
        assertSame(child, link(stubs = false))
        assertSame(child, link(stubs = true))
        assertSame(child, find(1))
    }

    @Test
    fun testAnsweredLinkKeepsAnswer() {
        val door = TestMock(null, FIND)
        state.stub<Any>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns door

        assertSame(door, link(stubs = true))
        assertEquals(emptyList(), specs)
    }

    @Test
    fun testLinkAppliesOnlyToItsCall() {
        val other = TestMock(null, FIND)

        assertFailsWith<LokkiException> {
            GeneratedMock.withoutStateChanges {
                GeneratedMock.link(parent, FIND, true, factory, null) {
                    other.`lokki$state`.answer(FIND_MEMBER, arrayOf(1, true))
                }
            }
        }
        assertEquals(emptyList(), specs)
    }

    @Test
    fun testLinkWithoutFactoryExplainsProblem() {
        val exception = assertFailsWith<LokkiException> {
            GeneratedMock.withoutStateChanges {
                GeneratedMock.link(parent, FIND, true, null, "'Engine' is final and cannot be mocked") { find(1) }
            }
        }

        assertTrue(
            exception.message!!.endsWith(
                "\nThe call is a link of a call chain in a stubbed or verified call: stub it first, " +
                    "a child mock cannot be created: 'Engine' is final and cannot be mocked",
            ),
        )
    }

    @Test
    fun testEquivalentMatchersShareChild() {
        val any = state.link(FIND, arrayOf(AnyMatcher, EqMatcher(true)), true, factory)
        val lambda = state.link(FIND, arrayOf(Matchers.match { _: Int -> false }, EqMatcher(true)), true, factory)
        val slot = state.link(FIND, arrayOf(Matchers.capture(CapturingSlot<Int>()), EqMatcher(true)), true, factory)
        val one = state.link(FIND, arrayOf(EqMatcher(1), EqMatcher(true)), true, factory)

        assertSame(any, lambda)
        assertSame(any, slot)
        assertNotSame(any, one)
        assertSame(one, link(stubs = false))
        assertSame(one, find(1))
        assertSame(any, find(2))
    }

    @Test
    fun testMatcherEquivalence() {
        val user = object : Matcher<Any?> {
            override fun match(arg: Any?): Boolean = false
        }
        assertTrue(arrayOf(Matchers.typed<Int>(AnyMatcher)).isEquivalentTo(arrayOf(user)))
        assertTrue(arrayOf(Matchers.eq(intArrayOf(1))).isEquivalentTo(arrayOf(EqMatcher(intArrayOf(1)))))
        assertTrue(arrayOf(Matchers.more(1)).isEquivalentTo(arrayOf(Matchers.more(1))))
        assertFalse(arrayOf(Matchers.more(1)).isEquivalentTo(arrayOf(Matchers.less(1))))
        assertFalse(arrayOf(Matchers.more(1)).isEquivalentTo(arrayOf(Matchers.more(2))))
        assertTrue(
            arrayOf(Matchers.and(Matchers.more(1), Matchers.match { _: Int -> true }))
                .isEquivalentTo(arrayOf(Matchers.and(Matchers.more(1), AnyMatcher))),
        )
        assertFalse(
            arrayOf(Matchers.or(AnyMatcher, AnyMatcher)).isEquivalentTo(arrayOf(Matchers.and(AnyMatcher, AnyMatcher))),
        )
        assertFalse(arrayOf(Matchers.isNull()).isEquivalentTo(arrayOf(Matchers.isNull(inverse = true))))
        assertFalse(arrayOf<Matcher<Any?>>(AnyMatcher).isEquivalentTo(arrayOf(AnyMatcher, AnyMatcher)))
    }

    @Test
    fun testRepeatedLinkIsRegisteredOnce() {
        val matchers = arrayOf<Matcher<Any?>>(AnyMatcher, EqMatcher(true))
        state.link(FIND, matchers, true, factory)
        state.link(FIND, arrayOf(Matchers.match { _: Int -> true }, EqMatcher(true)), true, factory)

        val exception = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, false)) }
        assertEquals(1, exception.message!!.lines().count { it.contains(".find(any(), eq(true))") })
    }

    @Test
    fun testChildInheritsRelaxedFlags() {
        val relaxed = TestMock(null, FIND, flags = RELAXED or RELAX_UNIT_FUN or SPY)
        relaxed.`lokki$state`.link(FIND, arrayOf(AnyMatcher, AnyMatcher), true, factory)

        assertEquals(RELAXED or RELAX_UNIT_FUN, specs.single().flags)
    }

    @Test
    fun testGrandchildIsNamedByDepth() {
        val child = link(stubs = true) as TestMock
        val grandchild = child.`lokki$state`.link(FIND, arrayOf(AnyMatcher, AnyMatcher), true, factory)

        val label = child.`lokki$state`.label().removePrefix("child of ")
        assertEquals("Repo(child^2 of $label#${grandchild.id()})", grandchild.name())
    }

    @Test
    fun testClearMocksDropsChildren() {
        val child = link(stubs = true)
        state.clear(
            answers = false,
            recordedCalls = false,
            childMocks = true,
            verificationMarks = false,
            exclusionRules = false,
        )

        assertSame(child, find(1))
        assertNotSame(child, state.link(FIND, arrayOf(EqMatcher(1), EqMatcher(true)), false, factory))
    }

    @Test
    fun testRelaxedMockAnswersWithChild() {
        val relaxed = TestMock(null, FIND, flags = RELAXED)
        relaxed.relaxed = { ChildMock(factory) }
        val state = relaxed.`lokki$state`

        val one = state.answer(FIND_MEMBER, arrayOf(1, true))
        assertSame(one, state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertNotSame(one, state.answer(FIND_MEMBER, arrayOf(2, true)))
        assertSame(one, state.link(FIND, arrayOf(EqMatcher(1), EqMatcher(true)), false, factory))
        assertEquals(listOf(RELAXED, RELAXED), specs.map { it.flags })
        assertEquals(emptyList(), state.unansweredRecords())
    }

    @Test
    fun testRelaxedMockSuggestsChainedMocksInItsFile() {
        val relaxed = TestMock(null, FIND, flags = RELAXED)
        val state = relaxed.`lokki$state`
        val noDefault = "relaxed mock has no default for Engine returned by ${state.name}.find(7, true); stub the call"
        fun message(): String? = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(7, true)) }.message

        relaxed.relaxed = { NoRelaxedDefault("Engine", mockable = true, file = "CarTest.kt") }
        assertEquals("$noDefault or annotate CarTest.kt, which creates the mock, with @file:ChainedMocks", message())
        relaxed.relaxed = { NoRelaxedDefault("Engine", mockable = true) }
        assertEquals("$noDefault or annotate the file that creates the mock with @file:ChainedMocks", message())
        relaxed.relaxed = { NoRelaxedDefault("Engine", mockable = false, file = "CarTest.kt") }
        assertEquals(noDefault, message())
    }

    private fun link(stubs: Boolean): Any? =
        GeneratedMock.withoutStateChanges { GeneratedMock.link(parent, FIND, stubs, factory, null) { find(1) } }

    private fun find(id: Int): Any? = state.answer(FIND_MEMBER, arrayOf(id, true))

    private fun Any?.name(): String = (this as TestMock).`lokki$state`.name

    private fun Any?.id(): String = name().substringAfterLast('#').removeSuffix(")")

    private fun MockState.label(): String = name.substringAfter('(').removeSuffix(")")

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val FIND_MEMBER = 3
    }
}
