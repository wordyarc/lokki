package io.lokki.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchersTest {
    @Test
    fun testEqIsInverted() {
        assertTrue(Matchers.eq("a").match("a"))
        assertFalse(Matchers.eq("a", inverse = true).match("a"))
        assertTrue(Matchers.neq("a").match("b"))
        assertTrue(Matchers.neq("a").match(null))
        assertFalse(Matchers.neq(intArrayOf(1)).match(intArrayOf(1)))
    }

    @Test
    fun testRefEqComparesIdentity() {
        val value = listOf("a")

        assertTrue(Matchers.refEq(value).match(value))
        assertFalse(Matchers.refEq(value).match(listOf("a")))
        assertTrue(Matchers.nrefEq(value).match(listOf("a")))
        assertFalse(Matchers.refEq(value, inverse = true).match(value))
    }

    @Test
    fun testIsNull() {
        assertTrue(Matchers.isNull().match(null))
        assertFalse(Matchers.isNull().match("a"))
        assertTrue(Matchers.isNull(inverse = true).match("a"))
    }

    @Test
    fun testComparisons() {
        assertTrue(Matchers.cmpEq(5).match(5))
        assertFalse(Matchers.cmpEq(5).match(6))
        assertTrue(Matchers.more(5).match(6))
        assertFalse(Matchers.more(5).match(5))
        assertTrue(Matchers.more(5, andEquals = true).match(5))
        assertTrue(Matchers.less(5).match(4))
        assertFalse(Matchers.less(5).match(5))
        assertTrue(Matchers.less(5, andEquals = true).match(5))
        assertFalse(Matchers.more(5).match(null))
    }

    @Test
    fun testRangeBounds() {
        val inclusive = Matchers.range(1, 3)
        val exclusive = Matchers.range(1, 3, fromInclusive = false, toInclusive = false)

        assertContentEquals(listOf(false, true, true, true, false), (0..4).map(inclusive::match))
        assertContentEquals(listOf(false, false, true, false, false), (0..4).map(exclusive::match))
    }

    @Test
    fun testComposites() {
        val even = Matchers.match { value: Int -> value % 2 == 0 }

        assertTrue(Matchers.and(even, Matchers.more(2)).match(4))
        assertFalse(Matchers.and(even, Matchers.more(2)).match(2))
        assertTrue(Matchers.or(even, Matchers.more(2)).match(3))
        assertFalse(Matchers.or(even, Matchers.more(2)).match(1))
        assertTrue(Matchers.not(even).match(1))
    }

    @Test
    fun testOnlyMatchNullableReceivesNull() {
        assertFalse(Matchers.match { _: String -> true }.match(null))
        assertTrue(Matchers.matchNullable { value: String? -> value == null }.match(null))
        assertFalse(Matchers.matchNullable { value: String? -> value == null }.match("a"))
    }

    @Test
    fun testOfTypeRejectsNull() {
        assertTrue(Matchers.ofType<String>().match("a"))
        assertFalse(Matchers.ofType<String>().match(1))
        assertFalse(Matchers.ofType<String>().match(null))
        assertTrue(Matchers.ofType<String?>().match(null))
    }

    @Test
    fun testTypedMatcherReceivesOnlyItsTypeAndNull() {
        val received = ArrayList<Any?>()
        val matcher = Matchers.typed<Int>(Matchers.matchNullable { value: Int? -> received.add(value) })

        assertTrue(matcher.match(1))
        assertTrue(matcher.match(null))
        assertFalse(matcher.match(1L))
        assertContentEquals(listOf(1, null), received)
    }

    @Test
    fun testArrayIsMatchedByElements() {
        val matcher = ArrayMatcher(arrayOf(AnyMatcher, EqMatcher("b")))

        assertTrue(matcher.match(arrayOf("a", "b")))
        assertFalse(matcher.match(arrayOf("a", "c")))
        assertFalse(matcher.match(arrayOf("a", "b", "c")))
        assertFalse(matcher.match(listOf("a", "b")))
        assertTrue(ArrayMatcher(arrayOf(AnyMatcher, Matchers.more(1))).match(intArrayOf(0, 2)))
        assertTrue(ArrayMatcher(emptyArray()).match(LongArray(0)))
    }

    @Test
    fun testSpreadMatchesEveryElementByValue() {
        val matcher = ArrayMatcher(arrayOf(AnyMatcher, *Matchers.spread(intArrayOf(2, 3))))

        assertTrue(matcher.match(intArrayOf(1, 2, 3)))
        assertFalse(matcher.match(intArrayOf(1, 3, 2)))
        assertTrue(ArrayMatcher(Matchers.spread(arrayOf(intArrayOf(1)))).match(arrayOf(intArrayOf(1))))
    }
}
