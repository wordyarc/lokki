package io.lokki

import io.lokki.internal.RegistrationScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatcherScopeTest {
    private val scope = RegistrationScope()

    @Test
    fun testMatcherFunctionReturnsPlaceholderOfItsType() {
        assertEquals(0, scope.any<Int>())
        assertEquals(false, scope.any<Boolean>())
        assertEquals(0L, scope.eq(5L))
        assertEquals('\u0000', scope.any<Char>())
        assertEquals(0.0, scope.any<Double>())
        assertEquals<Any?>(null, scope.any<String?>())
        assertEquals<Any?>(null, scope.any<List<Int>?>())
    }

    @Test
    fun testMatcherOfHelperIsTakenFromTheFrame() {
        val matcher = scope.matcherOf(scope.more(5))

        assertTrue(matcher.match(6))
        assertFalse(matcher.match(5))
        assertFalse(matcher.match(6L))
    }

    @Test
    fun testValueOfHelperWithoutMatchersIsMatchedByEquality() {
        val matcher = scope.matcherOf("a")

        assertTrue(matcher.match("a"))
        assertFalse(matcher.match("b"))
    }

    @Test
    fun testCompositesTakeOperandsFromTheFrame() {
        val matcher = scope.matcherOf(scope.or(scope.and(scope.more(1), scope.less(5)), scope.not(scope.less(10))))

        assertEquals(listOf(false, true, false, true), listOf(1, 3, 7, 10).map(matcher::match))
    }

    @Test
    fun testFrameIsEmptyAfterTheMatcherIsTaken() {
        scope.matcherOf(scope.any<Int>())

        assertTrue(scope.matcherOf(1).match(1))
        assertFalse(scope.matcherOf(1).match(2))
    }

    @Test
    fun testHelperMustProduceOneMatcher() {
        scope.any<Int>()

        val exception = assertFailsWith<LokkiException> { scope.matcherOf(scope.any<Int>()) }

        assertEquals("matcher helper produced 2 matchers", exception.message)
    }

    @Test
    fun testOperandOfCompositeMustBeMatcher() {
        val exception = assertFailsWith<LokkiException> { scope.and(1, scope.more(0)) }

        assertEquals("an operand of and, or, not in a matcher helper is not a matcher", exception.message)
    }

    @Test
    fun testCustomMatcherReceivesOnlyItsType() {
        val matcher = scope.matcherOf(scope.match(Matcher<String> { it.isEmpty() }))

        assertTrue(matcher.match(""))
        assertFalse(matcher.match("a"))
        assertFalse(matcher.match(1))
        assertFalse(matcher.match(null))
    }

    @Test
    fun testWithArgOfHelperIsTyped() {
        val received = ArrayList<Any?>()
        val withArg = scope.matcherOf(scope.withArg<Int> { received += it })
        val withNullableArg = scope.matcherOf(scope.withNullableArg<Int> { received += it })

        assertTrue(withArg.match(1))
        assertFalse(withArg.match("a"))
        assertFalse(withArg.match(null))
        assertTrue(withNullableArg.match(null))
        assertFalse(withNullableArg.match("a"))
        assertEquals(listOf<Any?>(1, null), received)
    }

    @Test
    fun testCaptureOfHelperIsTyped() {
        val slot = slot<Int>()
        val matcher = scope.matcherOf(scope.capture(slot))

        assertTrue(matcher.match(1))
        assertFalse(matcher.match("a"))
    }
}
