package io.lokki.internal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqMatcherTest {
    @Test
    fun testValuesAreComparedByEquals() {
        assertTrue(EqMatcher("a").match("a"))
        assertTrue(EqMatcher(null).match(null))
        assertFalse(EqMatcher("a").match("b"))
        assertFalse(EqMatcher("a").match(null))
        assertFalse(EqMatcher(null).match("a"))
    }

    @Test
    fun testArraysAreComparedByContent() {
        assertTrue(EqMatcher(intArrayOf(1, 2)).match(intArrayOf(1, 2)))
        assertTrue(EqMatcher(arrayOf<Any>(arrayOf("a"), intArrayOf(1))).match(arrayOf<Any>(arrayOf("a"), intArrayOf(1))))
        assertFalse(EqMatcher(intArrayOf(1, 2)).match(intArrayOf(2, 1)))
        assertFalse(EqMatcher(intArrayOf(1)).match(longArrayOf(1)))
        assertFalse(EqMatcher(arrayOf(1)).match(intArrayOf(1)))
    }
}
