package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Matcher
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.coroutines.suspendCoroutine

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
    fun testWithArgMatchesWhenTheBlockDoesNotFail() {
        val received = ArrayList<Any?>()
        val withArg = Matchers.withArg { value: Int ->
            received += actual
            checkEquals(1)
            received += value
        }
        val withNullableArg = Matchers.withNullableArg { value: Int? -> received += value }

        assertTrue(withArg.match(1))
        assertFalse(withArg.match(2))
        assertFalse(withArg.match(null))
        assertTrue(withNullableArg.match(null))
        assertContentEquals(listOf(1, 1, 2, null), received)
    }

    @Test
    fun testCoMatchersCompleteSuspendBlocksWithoutSuspension() {
        val received = ArrayList<Any?>()
        val coMatch = Matchers.coMatch { value: Int -> answer(value % 2 == 0) }
        val coMatchNullable = Matchers.coMatchNullable { value: Int? -> answer(value == null) }
        val coWithArg = Matchers.coWithArg { value: Int ->
            received += answer(actual)
            checkEquals(1)
        }
        val coWithNullableArg = Matchers.coWithNullableArg { value: Int? -> received += answer(value) }

        assertContentEquals(listOf(true, false, false), listOf(2, 3, null).map(coMatch::match))
        assertContentEquals(listOf(true, false), listOf(null, 1).map(coMatchNullable::match))
        assertTrue(coWithArg.match(1))
        assertFalse(coWithArg.match(2))
        assertFalse(coWithArg.match(null))
        assertTrue(coWithNullableArg.match(null))
        assertContentEquals(listOf(1, 2, null), received)
    }

    @Test
    fun testCoMatchersThrowWhenTheBlockSuspends() {
        val matchers = mapOf(
            "coMatch" to Matchers.coMatch { _: Int -> suspended() },
            "coMatchNullable" to Matchers.coMatchNullable { _: Int? -> suspended() },
            "coWithArg" to Matchers.coWithArg { _: Int -> suspended() },
            "coWithNullableArg" to Matchers.coWithNullableArg { _: Int? -> suspended() },
        )

        for ((name, matcher) in matchers) {
            val exception = assertFailsWith<LokkiException> { matcher.match(1) }
            assertEquals("$name suspended; a matcher must complete without suspension", exception.message)
        }
    }

    @Test
    fun testAssertionErrorOfFunctionMatcherIsReported() {
        val failure = AssertionError("not positive")
        val matchers = listOf(
            Matchers.withArg { value: Int -> if (value <= 0) throw failure },
            Matchers.withNullableArg { value: Int? -> if (value == null || value <= 0) throw failure },
            Matchers.match { value: Int -> if (value <= 0) throw failure else true },
            Matchers.matchNullable { value: Int? -> if (value == null || value <= 0) throw failure else true },
            Matchers.coWithArg { value: Int -> if (answer(value) <= 0) throw failure },
            Matchers.coMatch { value: Int -> if (answer(value) <= 0) throw failure else true },
        )

        for (matcher in matchers) {
            val errors = ArrayList<AssertionError>()
            assertTrue(matcher.match(1, errors))
            assertFalse(matcher.match(-1, errors))
            assertFalse(matcher.match(-1))
            assertEquals(listOf(failure), errors)
        }
    }

    @Test
    fun testCompositesReportAssertionErrorsOfOperands() {
        val failure = AssertionError("not positive")
        val positive = Matchers.withArg { value: Int -> if (value <= 0) throw failure }
        val errors = ArrayList<AssertionError>()

        assertTrue(Matchers.not(positive).match(-1, errors))
        assertFalse(Matchers.and(Matchers.typed<Int>(positive), AnyMatcher).match(-2, errors))
        assertTrue(Matchers.or(positive, EqMatcher(-3)).match(-3, errors))
        assertFalse(Matchers.typed<Int>(positive).match(-1L, errors))
        assertFalse(ArrayMatcher(arrayOf(AnyMatcher, positive)).match(arrayOf(1, -4), errors))
        assertEquals(List(4) { failure }, errors)
    }

    @Test
    fun testOtherExceptionsOfFunctionMatcherAreThrown() {
        val failure = IllegalStateException("broken")
        val errors = ArrayList<AssertionError>()
        val withArg = Matchers.withArg { _: Int -> throw failure }
        val match = Matchers.match { _: Int -> throw failure }

        assertSame(failure, assertFailsWith<IllegalStateException> { withArg.match(1, errors) })
        assertSame(failure, assertFailsWith<IllegalStateException> { match.match(1) })
        assertEquals(emptyList(), errors)
    }

    @Test
    fun testAssertionErrorOfMatcherObjectIsThrown() {
        val failure = AssertionError("not positive")
        val matcher = Matchers.match(Matcher<Int> { throw failure })

        assertSame(failure, assertFailsWith<AssertionError> { matcher.match(1, ArrayList()) })
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

    @Test
    fun testVarargMatcherMatchesElementsBetweenPrefixAndPostfix() {
        val matcher = Matchers.varargOf(arrayOf(EqMatcher("a")), Matchers.anyVararg<String>(), arrayOf(EqMatcher("z")))

        assertTrue(matcher.match(arrayOf("a", "z")))
        assertTrue(matcher.match(arrayOf("a", "b", "c", "z")))
        assertFalse(matcher.match(arrayOf("a")))
        assertFalse(matcher.match(arrayOf("b", "z")))
        assertFalse(matcher.match(arrayOf("a", "b")))
        assertFalse(matcher.match(listOf("a", "z")))
        assertFalse(matcher.match(null))
    }

    @Test
    fun testVarargAllAndVarargAny() {
        val all = Matchers.varargAllInt { it > 0 }
        val any = Matchers.varargAnyInt { it > 0 }

        assertTrue(all.match(intArrayOf(1, 2)))
        assertFalse(all.match(intArrayOf(1, -2)))
        assertTrue(all.match(IntArray(0)))
        assertTrue(any.match(intArrayOf(-1, 2)))
        assertFalse(any.match(intArrayOf(-1, -2)))
        assertFalse(any.match(IntArray(0)))
    }

    @Test
    fun testVarargMatchersAreTyped() {
        assertTrue(Matchers.anyVararg<String>().match(arrayOf("a", null)))
        assertFalse(Matchers.anyVararg<String>().match(arrayOf<Any>("a", 1)))
        assertFalse(Matchers.varargAll<String> { true }.match(arrayOf("a", null)))
        assertTrue(Matchers.varargAllNullable<String> { true }.match(arrayOf("a", null)))
        assertFalse(Matchers.varargAny<String> { true }.match(arrayOf<Any?>(1, null)))
        assertTrue(Matchers.varargAnyNullable<String> { it == null }.match(arrayOf<Any?>(1, null)))
    }

    @Test
    fun testVarargScopeHasPositionInTheWholeArray() {
        val positions = ArrayList<String>()
        val matcher = Matchers.varargOf(
            arrayOf(AnyMatcher),
            Matchers.varargAll<String> { positions += "$position/$nArgs:$it"; true },
            arrayOf(AnyMatcher),
        )

        assertTrue(matcher.match(arrayOf("a", "b", "c", "d")))
        assertEquals(listOf("1/4:b", "2/4:c"), positions)
    }

    @Test
    fun testVarargMatcherCapturesPrefixAndPostfix() {
        val first = CapturingSlot<String>()
        val last = CapturingSlot<String>()
        val matcher = Matchers.varargOf(
            arrayOf(Matchers.capture(first)),
            Matchers.anyVararg<String>(),
            arrayOf(Matchers.capture(last)),
        )

        assertTrue(matcher.match(arrayOf("a", "b", "c")))
        (matcher as CapturingMatcher).capture(arrayOf("a", "b", "c"))
        assertEquals("a", first.captured)
        assertEquals("c", last.captured)
    }

    @Test
    fun testAssertionErrorOfVarargPredicateIsReported() {
        val failure = AssertionError("not positive")
        val matcher = Matchers.varargOf(
            arrayOf(Matchers.withArg { value: Int -> if (value <= 0) throw failure }),
            Matchers.varargAllInt { if (it <= 0) throw failure else true },
            emptyArray(),
        )
        val errors = ArrayList<AssertionError>()

        assertTrue(matcher.match(intArrayOf(1, 2), errors))
        assertFalse(matcher.match(intArrayOf(1, -2), errors))
        assertFalse(matcher.match(intArrayOf(-1, 2), errors))
        assertEquals(List(2) { failure }, errors)
    }

    @Test
    fun testOnlyVarargMatcherIsSpread() {
        val exception = assertFailsWith<LokkiException> { Matchers.varargOf(emptyArray(), AnyMatcher, emptyArray()) }

        assertEquals(
            "any() cannot be spread into a vararg parameter: " +
                "only anyVararg, varargAll and varargAny match a part of the array",
            exception.message,
        )
    }

    @Test
    fun testCaptureLambdaMatchesEveryValueButNull() {
        for (matcher in listOf(Matchers.captureLambda(), Matchers.captureCoroutine())) {
            assertTrue(matcher.match { 1 })
            assertFalse(matcher.match(null))
            assertTrue(matcher.capturesLambda)
        }
        val typed = Matchers.typed<() -> Int>(Matchers.captureLambda())

        assertTrue(typed.capturesLambda)
        assertFalse(typed.match { value: Int -> value })
        assertFalse(Matchers.capture(CapturingSlot<() -> Int>()).capturesLambda)
    }

    @Test
    fun testMatchersDescribeThemselves() {
        val descriptions = listOf(
            AnyMatcher,
            EqMatcher("a"),
            Matchers.neq(intArrayOf(1, 2)),
            Matchers.refEq(null),
            Matchers.nrefEq("a"),
            Matchers.isNull(),
            Matchers.isNull(inverse = true),
            Matchers.cmpEq(5),
            Matchers.more(5, andEquals = true),
            Matchers.less(5),
            Matchers.range(1, 3, toInclusive = false),
            Matchers.or(Matchers.not(EqMatcher(1)), Matchers.ofType<String>()),
            Matchers.match { _: Int -> true },
            Matchers.matchNullable { _: Int? -> true },
            Matchers.withArg { _: Int -> },
            Matchers.withNullableArg { _: Int? -> },
            Matchers.typed<Int>(Matchers.capture(ArrayList<Int>())),
            Matchers.captureNullable(CapturingSlot<Int?>()),
            Matchers.captureLambda(),
            ArrayMatcher(arrayOf(AnyMatcher, EqMatcher { })),
            Matchers.varargOf(arrayOf(EqMatcher("a")), Matchers.anyVararg<String>(), arrayOf(AnyMatcher)),
            Matchers.varargAllNullable<String> { true },
            Matchers.varargAnyDouble { true },
        ).map { it.toString() }

        assertContentEquals(
            listOf(
                "any()",
                "eq(a)",
                "nonEq([1, 2])",
                "refEq(null)",
                "refNonEq(a)",
                "null()",
                "notNull()",
                "cmpEq(5)",
                "moreAndEquals(5)",
                "less(5)",
                "and(moreAndEquals(1), less(3))",
                "or(not(eq(1)), ofType(String))",
                "matcher()",
                "matcher()",
                "matcher()",
                "matcher()",
                "capture()",
                "slotCapture()",
                "slotCapture()",
                "[any(), eq(lambda {})]",
                "[eq(a), *anyVararg(), any()]",
                "[*varargAll()]",
                "[*varargAny()]",
            ),
            descriptions,
        )
    }

    @Test
    fun testMatcherObjectDescribesItself() {
        val even = object : Matcher<Int> {
            override fun match(arg: Int): Boolean = arg % 2 == 0

            override fun toString(): String = "even()"
        }
        val matcher = Matchers.match(even)

        assertTrue(matcher.match(2))
        assertFalse(matcher.match(3))
        assertFalse(matcher.match("a"))
        assertFalse(matcher.match(null))
        assertEquals("even()", matcher.toString())
    }

    @Test
    fun testEqualsOfTheSameInstanceIsNotCalled() {
        val unequal = object {
            override fun equals(other: Any?): Boolean = false
        }

        assertTrue(EqMatcher(unequal).match(unequal))
    }
}

private suspend fun <T> answer(value: T): T = value

private suspend fun suspended(): Boolean = suspendCoroutine { }
