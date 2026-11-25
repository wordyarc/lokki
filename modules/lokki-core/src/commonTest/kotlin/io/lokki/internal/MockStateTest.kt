package io.lokki.internal

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MockStateTest {
    private val owner = TestMock(
        null,
        FIND,
        "sample.Repo#kotlin.String.label()",
        aliases = arrayOf("sample.Named#kotlin.String.label()", "sample.Repo#kotlin.String.label()"),
    )
    private val state = owner.`lokki$state`()
    private val name = state.answer(TO_STRING, emptyArray())

    @Test
    fun testLastMatchingStubAnswers() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"
        state.stub<String>(FIND, arrayOf(EqMatcher(1), EqMatcher(false))) returns "one"

        assertEquals("one", state.answer(FIND_MEMBER, arrayOf(1, false)))
        assertEquals("any", state.answer(FIND_MEMBER, arrayOf(2, false)))
    }

    @Test
    fun testNoAnswerWithoutMatchingStub() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(false))) returns "any"

        state.stub<String>("sample.Repo#kotlin.String.label()", arrayOf(Matchers.more(5))) returns "label"

        val exception = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }

        assertEquals(
            "no answer found for $name.find(1, true) among the configured answers: " +
                "($name.find(any(), eq(false))\n$name.label(more(5)))" + CALLED_FIND,
            exception.message,
        )
    }

    @Test
    fun testMemberIsDescribedOnce() {
        val mock = TestMock(null, FIND)
        val descriptions = ArrayList<Int>()
        val describe = mock::`lokki$describe`
        val counting = object : GeneratedMock by mock {
            override fun `lokki$describe`(member: Int) = describe(member).also { descriptions += member }
        }
        val descriptor = mock.`lokki$state`().descriptor

        assertSame(descriptor.description(counting, FIND_MEMBER), descriptor.description(counting, FIND_MEMBER))
        assertEquals(listOf(FIND_MEMBER), descriptions)
    }

    @Test
    fun testMethodsOfAnyHaveIdentityAnswers() {
        assertEquals(true, state.answer(EQUALS, arrayOf(owner)))
        assertEquals(false, state.answer(EQUALS, arrayOf(Any())))
        assertEquals(false, state.answer(EQUALS, arrayOf(null)))
        assertEquals(owner.hashCode(), state.answer(HASH_CODE, emptyArray()))
        assertTrue(Regex("""Repo\(#\d+\)""").matches(name as String))
    }

    @Test
    fun testMethodsOfAnyAreStubbed() {
        state.stub<String>("kotlin.Any#toString()", emptyArray()) returns "repo"
        state.stub<Boolean>("kotlin.Any#equals(kotlin.Any?)", arrayOf(EqMatcher("other"))) returns true

        assertEquals("repo", state.answer(TO_STRING, emptyArray()))
        assertEquals(true, state.answer(EQUALS, arrayOf("other")))
        assertEquals(false, state.answer(EQUALS, arrayOf("another")))
        val exception = assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
        assertTrue(exception.message!!.startsWith("no answer found for $name.find(1, true)"))
    }

    @Test
    fun testNameOfMockPrecedesItsNumber() {
        val named = TestMock("repo", FIND).`lokki$state`()

        assertTrue(Regex("""Repo\(repo#\d+\)""").matches(named.answer(TO_STRING, emptyArray()) as String))
    }

    @Test
    fun testMemberIsFoundByEveryKey() {
        state.stub<String>("sample.Named#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        assertEquals("label", state.answer(LABEL_MEMBER, arrayOf("x")))
    }

    @Test
    fun testKeyOfTwoMembersIsRejected() {
        val state = TestMock(null, FIND, FIND).`lokki$state`()

        val exception = assertFailsWith<LokkiException> { state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) }

        assertEquals("Repo has two members with the key $FIND", exception.message)
    }

    @Test
    fun testUnknownMember() {
        val exception = assertFailsWith<LokkiException> { state.stub<String>("sample.Repo#save()", emptyArray()) }

        assertEquals("Repo has no member sample.Repo#save()", exception.message)
    }

    @Test
    fun testDefaultsAreCapturedFromTheCallOfTheMember() {
        val arguments = state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) {
            state.answer(FIND_MEMBER, arrayOf(0, false))
        }

        assertContentEquals(arrayOf<Any?>(0, false), arguments)
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(0, false)) }
    }

    @Test
    fun testOtherMembersAnswerWhileDefaultsAreCaptured() {
        state.stub<String>("sample.Repo#kotlin.String.label()", arrayOf(AnyMatcher)) returns "label"

        val arguments = state.captureDefaults(FIND, arrayOf("id", null)) {
            state.answer(FIND_MEMBER, arrayOf(state.answer(LABEL_MEMBER, arrayOf("x")), false))
        }

        assertContentEquals(arrayOf<Any?>("label", false), arguments)
    }

    @Test
    fun testDefaultThatDependsOnPlaceholderIsUnstable() {
        val exception = assertFailsWith<LokkiException> {
            state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { alternate ->
                state.answer(FIND_MEMBER, arrayOf(if (alternate) 1 else 0, alternate))
            }
        }

        assertEquals(
            "default value of 'includeDeleted' is unstable, has no equals, or depends on a matcher argument; " +
                "pass it explicitly or use any()",
            exception.message,
        )
    }

    @Test
    fun testPlaceholdersOfPassedArgumentsMayDiffer() {
        val arguments = state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { alternate ->
            state.answer(FIND_MEMBER, arrayOf(if (alternate) 1 else 0, true))
        }

        assertContentEquals(arrayOf<Any?>(0, true), arguments)
    }

    @Test
    fun testFailureOfDefaultValueIsReported() {
        val failure = IllegalStateException("no clock")

        val exception = assertFailsWith<LokkiException> {
            state.captureDefaults(FIND, arrayOf(null, "includeDeleted")) { throw failure }
        }

        assertEquals(
            "default values of Repo.find cannot be evaluated; pass them explicitly or use any()",
            exception.message,
        )
        assertSame(failure, exception.cause)
    }

    @Test
    fun testSuspendDefaultsAreCapturedFromTheCallOfTheMember() {
        val arguments = state.coCaptureDefaults(FIND, arrayOf(null, "includeDeleted")) {
            state.coAnswer<String>(FIND_MEMBER, arrayOf(0, false))
        }

        assertContentEquals(arrayOf<Any?>(0, false), arguments)
        assertEquals(emptyList(), records())
    }

    @Test
    fun testFailureOfSuspendDefaultValueIsReported() {
        val failure = IllegalStateException("no clock")

        val exception = assertFailsWith<LokkiException> {
            state.coCaptureDefaults(FIND, arrayOf(null, "includeDeleted")) { throw failure }
        }

        assertEquals(
            "default values of Repo.find cannot be evaluated; pass them explicitly or use any()",
            exception.message,
        )
        assertSame(failure, exception.cause)
    }

    @Test
    fun testAnswersFollowOneAnotherAndTheLastRepeats() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a" andThen "b" andThenMany listOf("c", "d")

        assertEquals(listOf<Any?>("a", "b", "c", "d", "d"), List(5) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testAnswerAddedLaterFollowsTheRepeatedOne() {
        val stub = state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a"
        state.answer(FIND_MEMBER, arrayOf(1, true))
        state.answer(FIND_MEMBER, arrayOf(1, true))
        stub andThen "b"

        assertEquals(listOf<Any?>("a", "b", "b"), List(3) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testManyAnswersAndExceptions() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher))
            .returnsMany("a", "b") andThenThrows first andThenThrowsMany listOf(second)

        assertEquals(listOf<Any?>("a", "b"), List(2) { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(first, assertFailsWith<IllegalStateException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(second, assertFailsWith<IllegalArgumentException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
        assertSame(second, assertFailsWith<IllegalArgumentException> { state.answer(FIND_MEMBER, arrayOf(1, true)) })
    }

    @Test
    fun testStubNeedsAnAnswer() {
        val stub = state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher))

        assertEquals("a stub needs at least one answer", assertFailsWith<LokkiException> { stub returnsMany emptyList() }.message)
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
    }

    @Test
    fun testAnswerReceivesTheCall() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { call ->
            "${call.invocation.self === owner} $nArgs ${firstArg<Int>() + 1} ${secondArg<Boolean>()} ${lastArg<Boolean>()} ${arg<Int>(0)} $args"
        }

        assertEquals("true 2 8 true true 7 [7, true]", state.answer(FIND_MEMBER, arrayOf(7, true)))
    }

    @Test
    fun testMockOfNewerAbiIsRejected() {
        val newer = LokkiAbi.VERSION + 1
        val exception = assertFailsWith<LokkiException> {
            MockState(owner, MockSpec(null, 0), MockDescriptor("Repo", newer, emptyArray()))
        }

        assertEquals(
            "the mock of Repo was compiled by a newer lokki compiler plugin than lokki-core on the classpath: " +
                "it needs ABI $newer, lokki-core has ABI ${LokkiAbi.VERSION}; " +
                "use the version of the lokki Gradle plugin for all lokki dependencies",
            exception.message,
        )
        MockState(owner, MockSpec(null, 0), MockDescriptor("Repo", OLDEST_ABI, emptyArray()))
    }

    @Test
    fun testMockOfOlderAbiThanSupportedIsRejected() {
        val older = OLDEST_ABI - 1
        val exception = assertFailsWith<LokkiException> {
            MockState(owner, MockSpec(null, 0), MockDescriptor("Repo", older, emptyArray()))
        }

        assertEquals(
            "the mock of Repo was compiled by an older lokki compiler plugin than lokki-core on the classpath " +
                "supports: it has ABI $older, lokki-core needs ABI $OLDEST_ABI or newer; " +
                "recompile it with the version of the lokki Gradle plugin for all lokki dependencies",
            exception.message,
        )
    }

    @Test
    fun testAnswerReceivesTheMatcherOfItsStub() {
        val matchers = ArrayList<Any>()
        state.stub<String>(FIND, arrayOf(EqMatcher(7), AnyMatcher)) answers { call ->
            matchers += call.matcher
            "$matcher ${matcher.args} ${matcher.method.name} ${matcher.self === owner} ${matcher === call.matcher}"
        }

        assertEquals("$name.find(eq(7), any()) [eq(7), any()] find true true", state.answer(FIND_MEMBER, arrayOf(7, true)))
        state.answer(FIND_MEMBER, arrayOf(7, false))
        assertSame(matchers[0], matchers[1])
    }

    @Test
    fun testSuspendCallIsAnsweredLikeOtherCalls() {
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns "a"
        state.stub<String>(FIND, arrayOf(EqMatcher(2), AnyMatcher)) answers { "answered ${firstArg<Int>()}" }

        assertEquals(Result.success("a"), start { state.coAnswer<String>(FIND_MEMBER, arrayOf(1, true)) }())
        assertEquals(Result.success("answered 2"), start { state.coAnswer<String>(FIND_MEMBER, arrayOf(2, true)) }())
        assertEquals(
            "no answer found for $name.find(3, true)",
            start { state.coAnswer<String>(FIND_MEMBER, arrayOf(3, true)) }()?.exceptionOrNull()?.message
                ?.substringBefore(" among"),
        )
        assertEquals(listOf("$name.find(1, true)", "$name.find(2, true)", "$name.find(3, true)"), records())
    }

    @Test
    fun testSuspendingAnswerSuspendsTheCall() {
        var pending: Continuation<Unit>? = null
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) coAnswers {
            suspendCoroutine { pending = it }
            "late ${firstArg<Int>()}"
        }

        val outcome = start { state.coAnswer<String>(FIND_MEMBER, arrayOf(7, true)) }
        assertNull(outcome())
        pending!!.resume(Unit)

        assertEquals(Result.success("late 7"), outcome())
    }

    @Test
    fun testSuspendingAnswerOfNonSuspendCallMustNotSuspend() {
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) coAnswers { "at once" }
        state.stub<String>(FIND, arrayOf(EqMatcher(2), AnyMatcher)) coAnswers {
            suspendCoroutine<Unit> { }
            "never"
        }

        assertEquals("at once", state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertEquals(
            "the answer of non-suspend $name.find(2, true) suspended: an answer of a non-suspend member, " +
                "such as coAnswers, coAndThen or just Awaits, must complete without suspension",
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(2, true)) }.message,
        )
    }

    @Test
    fun testToStringDescribesStubsAndCalls() {
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns "one" andThen "two"
        state.stub<String>(FIND, arrayOf(AnyMatcher, EqMatcher(true))) throws IllegalStateException("closed")
        state.stub<String>(FIND, arrayOf(EqMatcher(2), AnyMatcher)) answers { "two" }
        state.stub<String>(FIND, arrayOf(EqMatcher(3), AnyMatcher)) returnsArgument 0
        state.stub<String>(FIND, arrayOf(EqMatcher(4), AnyMatcher)) coAnswers { "four" }
        state.answer(FIND_MEMBER, arrayOf(1, false))
        state.answer(FIND_MEMBER, arrayOf(2, false))

        assertEquals(
            "MockState(name=$name, scope=null, stubs=[" +
                "find(eq(1), any()) returns one andThen returns two, " +
                "find(any(), eq(true)) throws java.lang.IllegalStateException: closed, " +
                "find(eq(2), any()) answers { }, " +
                "find(eq(3), any()) returnsArgument 0, " +
                "find(eq(4), any()) coAnswers { }" +
                "], calls=[find(1, false), find(2, false)])",
            state.toString(),
        )
    }

    @Test
    fun testLambdaIsTheLastCapturedArgumentOfTheCall() {
        val captures = arrayOf(Matchers.captureLambda(), Matchers.typed<() -> String>(Matchers.captureLambda()))
        state.stub<String>(FIND, captures) answers { lambda<() -> String>().captured() }

        assertEquals("second", state.answer(FIND_MEMBER, arrayOf({ "first" }, { "second" })))
        assertEquals("third", state.answer(FIND_MEMBER, arrayOf({ "first" }, { "third" })))
    }

    @Test
    fun testSuspendedAnswerKeepsTheCoroutineOfItsCall() {
        val pending = ArrayList<Continuation<Unit>>()
        state.stub<String>(FIND, arrayOf(AnyMatcher, Matchers.captureCoroutine())) coAnswers {
            suspendCoroutine { pending += it }
            coroutine<suspend () -> String>().captured()
        }

        val first = start { state.coAnswer<String>(FIND_MEMBER, arrayOf(1, suspend { "first" })) }
        val second = start { state.coAnswer<String>(FIND_MEMBER, arrayOf(2, suspend { "second" })) }
        pending.forEach { it.resume(Unit) }

        assertEquals(listOf(Result.success("first"), Result.success("second")), listOf(first(), second()))
    }

    @Test
    fun testLambdaNeedsCaptureLambda() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { lambda<() -> String>().captured() }
        state.stub<String>(FIND, arrayOf(EqMatcher(2), AnyMatcher)) answers { coroutine<suspend () -> String>().toString() }

        assertEquals(
            "lambda() in the answer of $name.find(1, lambda {}) needs captureLambda() as an argument of the stubbed call",
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, { "x" })) }.message,
        )
        assertEquals(
            "coroutine() in the answer of $name.find(2, lambda {}) needs captureCoroutine() " +
                "as an argument of the stubbed call",
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(2, suspend { "x" })) }.message,
        )
    }

    @Test
    fun testOriginalReceivesTheMemberAndTheArgumentsOfTheCall() {
        val mock = TestMock("repo", FIND)
        val calls = ArrayList<Pair<Int, Array<Any?>>>()
        mock.original = { member, arguments -> calls += member to arguments; "original ${arguments[0]}" }
        mock.`lokki$state`().stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { callOriginal() + "!" }
        val arguments = arrayOf<Any?>(7, true)

        assertEquals("original 7!", mock.`lokki$state`().answer(FIND_MEMBER, arguments))
        assertEquals(FIND_MEMBER, calls.single().first)
        assertSame(arguments, calls.single().second)
    }

    @Test
    fun testMemberWithoutOriginal() {
        val mock = TestMock("repo", FIND)
        mock.`lokki$state`().stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { callOriginal() }
        val name = mock.`lokki$state`().answer(TO_STRING, emptyArray())

        assertEquals(
            "no original implementation for $name.find(7, true)",
            assertFailsWith<LokkiException> { mock.`lokki$state`().answer(FIND_MEMBER, arrayOf(7, true)) }.message,
        )
    }

    @Test
    fun testCoCallOriginalReceivesTheMemberAndTheArgumentsOfTheCall() {
        val mock = TestMock("repo", FIND, suspending = setOf(FIND))
        val calls = ArrayList<Pair<Int, Array<Any?>>>()
        var pending: Continuation<Unit>? = null
        mock.coOriginal = { member, arguments ->
            calls += member to arguments
            suspendCoroutine { pending = it }
            "original ${arguments[0]}"
        }
        mock.`lokki$state`().stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) coAnswers { coCallOriginal() + "!" }
        val arguments = arrayOf<Any?>(7, true)

        val outcome = start { mock.`lokki$state`().coAnswer<String>(FIND_MEMBER, arguments) }
        assertNull(outcome())
        pending!!.resume(Unit)

        assertEquals(Result.success("original 7!"), outcome())
        assertEquals(FIND_MEMBER, calls.single().first)
        assertSame(arguments, calls.single().second)
    }

    @Test
    fun testCallOriginalOfSuspendMemberMustNotSuspend() {
        val mock = TestMock("repo", FIND, suspending = setOf(FIND))
        mock.original = { _, _ -> throw AssertionError("a suspend member has no original in lokki\$original") }
        mock.coOriginal = { _, arguments ->
            if (arguments[0] == 2) suspendCoroutine<Unit> { }
            "original ${arguments[0]}"
        }
        val state = mock.`lokki$state`()
        val name = state.answer(TO_STRING, emptyArray())
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) answers { callOriginal() + "!" }

        assertEquals(Result.success("original 1!"), start { state.coAnswer<String>(FIND_MEMBER, arrayOf(1, true)) }())
        assertEquals(
            "callOriginal() of suspend $name.find(2, true) suspended; use coCallOriginal()",
            start { state.coAnswer<String>(FIND_MEMBER, arrayOf(2, true)) }()?.exceptionOrNull()?.message,
        )
    }

    @Test
    fun testCoCallOriginalOfNonSuspendMember() {
        val mock = TestMock("repo", FIND)
        mock.original = { _, arguments -> "original ${arguments[0]}" }
        mock.`lokki$state`().stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) coAnswers { coCallOriginal() + "!" }

        assertEquals("original 7!", mock.`lokki$state`().answer(FIND_MEMBER, arrayOf(7, true)))
    }

    @Test
    fun testCoAndThenAddsSuspendingAnswer() {
        var pending: Continuation<Unit>? = null
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "first" coAndThen {
            suspendCoroutine { pending = it }
            "then ${firstArg<Int>()}"
        }

        assertEquals(Result.success("first"), start { state.coAnswer<String>(FIND_MEMBER, arrayOf(1, true)) }())
        val outcome = start { state.coAnswer<String>(FIND_MEMBER, arrayOf(2, true)) }
        assertNull(outcome())
        pending!!.resume(Unit)
        assertEquals(Result.success("then 2"), outcome())
    }

    @Test
    fun testSpyAnswersWithTheOriginalWithoutStub() {
        val mock = TestMock("repo", FIND, flags = SPY)
        mock.original = { member, arguments ->
            when (member) {
                EQUALS -> arguments[0] == "same"
                HASH_CODE -> 42
                else -> "original ${arguments[0]}"
            }
        }
        val state = mock.`lokki$state`()
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns "one"

        assertEquals("one", state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertEquals("original 2", state.answer(FIND_MEMBER, arrayOf(2, true)))
        assertEquals(true, state.answer(EQUALS, arrayOf("same")))
        assertEquals(42, state.answer(HASH_CODE, emptyArray()))
        assertTrue((state.answer(TO_STRING, emptyArray()) as String).startsWith("Repo(repo#"))
        assertEquals(2, state.records().size)
        assertEquals(emptyList(), state.unansweredRecords())
    }

    @Test
    fun testDelegatingSpyComparesByIdentity() {
        val mock = TestMock("repo", FIND, flags = SPY or DELEGATE)
        mock.original = { member, arguments ->
            if (member <= TO_STRING) throw AssertionError("member $member was delegated")
            "delegate ${arguments[0]}"
        }
        val state = mock.`lokki$state`()

        assertEquals("delegate 2", state.answer(FIND_MEMBER, arrayOf(2, true)))
        assertEquals(true, state.answer(EQUALS, arrayOf(mock)))
        assertEquals(false, state.answer(EQUALS, arrayOf("other")))
        assertEquals(identityHashCode(mock), state.answer(HASH_CODE, emptyArray()))
    }

    @Test
    fun testSuspendCallOfSpyAnswersWithTheSuspendingOriginal() {
        val mock = TestMock("repo", FIND, flags = SPY, suspending = setOf(FIND))
        var pending: Continuation<Unit>? = null
        mock.coOriginal = { _, arguments ->
            suspendCoroutine { pending = it }
            "original ${arguments[0]}"
        }

        val outcome = start { mock.`lokki$state`().coAnswer<String>(FIND_MEMBER, arrayOf(7, true)) }
        assertNull(outcome())
        pending!!.resume(Unit)

        assertEquals(Result.success("original 7"), outcome())
    }

    @Test
    fun testAbstractUnitMemberOfSpyDoesNothing() {
        val mock = TestMock("repo", FIND, SAVE, flags = SPY, units = setOf(SAVE))
        val state = mock.`lokki$state`()
        val name = state.answer(TO_STRING, emptyArray())

        assertEquals(Unit, state.answer(SAVE_MEMBER, emptyArray()))
        assertEquals(
            "no original implementation for $name.find(7, true)",
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(7, true)) }.message,
        )
        assertEquals(listOf("$name.save()", "$name.find(7, true)"), state.records().map { it.toString() })
    }

    @Test
    fun testAbstractUnitMemberOfMockHasNoOriginal() {
        val mock = TestMock("repo", SAVE, units = setOf(SAVE))
        val state = mock.`lokki$state`()
        state.stub<Unit>(SAVE, emptyArray()) answers { callOriginal() }

        assertFailsWith<LokkiException> { state.answer(3, emptyArray()) }
    }

    @Test
    fun testRelaxedMockAnswersWithTheDefaultOfTheMember() {
        val mock = TestMock("repo", FIND, SAVE, flags = RELAXED)
        val defaults = mapOf<Int, Any?>(FIND_MEMBER to null, SAVE_MEMBER to Unit)
        mock.relaxed = defaults::getValue
        val state = mock.`lokki$state`()
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns "one"

        assertEquals("one", state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertEquals(null, state.answer(FIND_MEMBER, arrayOf(2, true)))
        assertEquals(Unit, state.answer(SAVE_MEMBER, emptyArray()))
        assertEquals(3, state.records().size)
        assertEquals(emptyList(), state.unansweredRecords())
    }

    @Test
    fun testRelaxedMockWithoutDefaultFails() {
        val mock = TestMock("repo", FIND, flags = RELAXED)
        mock.relaxed = { NoRelaxedDefault("Engine", mockable = false) }
        val state = mock.`lokki$state`()
        val name = state.answer(TO_STRING, emptyArray())

        assertEquals(
            "relaxed mock has no default for Engine returned by $name.find(7, true); stub the call",
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(7, true)) }.message,
        )
        assertEquals(listOf("$name.find(7, true)"), state.unansweredRecords().map { it.toString() })
    }

    @Test
    fun testRelaxedUnitFunAnswersOnlyUnitMembers() {
        val mock = TestMock("repo", FIND, SAVE, flags = RELAX_UNIT_FUN)
        val defaults = mapOf(FIND_MEMBER to "", SAVE_MEMBER to Unit)
        mock.relaxed = defaults::getValue
        val state = mock.`lokki$state`()
        val name = state.answer(TO_STRING, emptyArray())

        assertEquals(Unit, state.answer(SAVE_MEMBER, emptyArray()))
        assertEquals(
            "no answer found for $name.find(7, true) among the configured answers: ()" + CALLED_FIND,
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(7, true)) }.message,
        )
        assertEquals(listOf("$name.find(7, true)"), state.unansweredRecords().map { it.toString() })
    }

    @Test
    fun testStrictMockDoesNotAskForDefaults() {
        val mock = TestMock("repo", FIND)
        mock.relaxed = { throw AssertionError("a strict mock asked for the default of member $it") }

        assertTrue(
            assertFailsWith<LokkiException> { mock.`lokki$state`().answer(FIND_MEMBER, arrayOf(7, true)) }
                .message!!.startsWith("no answer found for "),
        )
    }

    @Test
    fun testArgumentIsReturned() {
        state.stub<Any?>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returnsArgument 1

        assertEquals(true, state.answer(FIND_MEMBER, arrayOf(7, true)))
    }

    @Test
    fun testArgumentOutOfRangeFailsAtRegistration() {
        for (index in listOf(-1, 2)) {
            val stub = state.stub<Any?>(FIND, arrayOf(AnyMatcher, AnyMatcher))

            assertEquals(
                "returnsArgument($index) is out of range: the number of arguments of Repo.find is 2",
                assertFailsWith<LokkiException> { stub returnsArgument index }.message,
            )
        }
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(1, true)) }
    }

    @Test
    fun testArgumentsAreCapturedAfterTheWholeCallMatched() {
        val ids = ArrayList<Int>()
        val flag = CapturingSlot<Boolean>()
        state.stub<String>(FIND, arrayOf(Matchers.capture(ids), EqMatcher(true))) returns "a"
        state.stub<String>(FIND, arrayOf(EqMatcher(5), Matchers.capture(flag))) returns "b"

        assertEquals("a", state.answer(FIND_MEMBER, arrayOf(1, true)))
        assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(2, false)) }
        assertEquals("b", state.answer(FIND_MEMBER, arrayOf(5, true)))

        assertEquals(listOf(1), ids)
        assertEquals(true, flag.captured)
    }

    @Test
    fun testRegistrationDoesNotChangeState() {
        val ids = ArrayList<Int>()
        state.stub<String>(FIND, arrayOf(Matchers.capture(ids), AnyMatcher)).returnsMany("a", "b")

        val answers = GeneratedMock.withoutStateChanges { List(2) { state.answer(FIND_MEMBER, arrayOf(1, true)) } }

        assertEquals(listOf<Any?>("a", "a"), answers)
        assertEquals(emptyList(), ids)
        assertEquals(listOf<Any?>("a", "b"), List(2) { state.answer(FIND_MEMBER, arrayOf(2, true)) })
        assertEquals(listOf(2, 2), ids)
    }

    @Test
    fun testEvaluationCompletesWithoutSuspension() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)).returnsMany("a", "b")

        val answers = GeneratedMock.withoutStateChanges {
            GeneratedMock.evaluate("coEvery") { List(2) { state.coAnswer<String>(FIND_MEMBER, arrayOf(1, true)) } }
        }

        assertEquals(listOf("a", "a"), answers)
        assertEquals(emptyList(), records())
    }

    @Test
    fun testEvaluationMustNotSuspend() {
        var pending: Continuation<Unit>? = null
        var evaluated = false

        val exception = assertFailsWith<LokkiException> {
            GeneratedMock.evaluate("coVerify") {
                suspendCoroutine { pending = it }
                evaluated = true
            }
        }

        assertEquals(
            "a receiver or an argument of coVerify suspended; evaluate it outside the block",
            exception.message,
        )
        pending!!.resume(Unit)
        assertTrue(evaluated)
    }

    @Test
    fun testEvaluationRethrowsFailures() {
        val failure = IllegalStateException("offline")

        val exception = assertFailsWith<IllegalStateException> { GeneratedMock.evaluate("coEvery") { throw failure } }

        assertSame(failure, exception)
    }

    @Test
    fun testExcludedCallsAreNotRecorded() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a"
        find(1, false)
        find(2, false)
        find(1, true)

        state.exclude(FIND, arrayOf(EqMatcher(1), AnyMatcher), current = false)
        find(1, false)
        find(3, false)
        assertEquals(
            listOf("$name.find(1, false)", "$name.find(2, false)", "$name.find(1, true)", "$name.find(3, false)"),
            records(),
        )

        state.exclude(FIND, arrayOf(AnyMatcher, EqMatcher(true)))
        assertEquals(listOf("$name.find(1, false)", "$name.find(2, false)", "$name.find(3, false)"), records())
        assertEquals("a", state.answer(FIND_MEMBER, arrayOf(4, true)))
        assertEquals(3, records().size)

        state.clear(
            answers = false,
            recordedCalls = false,
            childMocks = false,
            verificationMarks = false,
            exclusionRules = true,
        )
        find(4, true)
        assertEquals("$name.find(4, true)", records().last())
    }

    @Test
    fun testStubIsUsedByCallsOutsideRegistration() {
        state.stub<String>(FIND, arrayOf(EqMatcher(1), AnyMatcher)) returns "one"
        state.stub<String>(FIND, arrayOf(EqMatcher(2), AnyMatcher)) returns "two"
        state.stub<String>(FIND, arrayOf(EqMatcher(3), AnyMatcher)) returns "three"
        GeneratedMock.withoutStateChanges { find(2, false) }
        find(3, false)

        assertEquals(
            "Unnecessary stubbings detected.\nFollowing stubbings are not used, either because there are " +
                "unnecessary or because tested code doesn't call them :\n\n" +
                "1) $name.find(eq(1), any())\n2) $name.find(eq(2), any())",
            assertFailsWith<AssertionError> { state.checkUnnecessaryStub() }.message,
        )

        find(1, true)
        find(2, true)
        state.checkUnnecessaryStub()
    }

    @Test
    fun testClearRemovesSelectedState() {
        state.stub<String>(FIND, arrayOf(AnyMatcher, AnyMatcher)) returns "a"
        find(1, false)
        state.markVerified(state.records())
        find(2, false)

        state.clear(
            answers = false,
            recordedCalls = false,
            childMocks = false,
            verificationMarks = true,
            exclusionRules = false,
        )
        assertContains(
            assertFailsWith<AssertionError> { state.confirmVerified() }.message!!,
            "Verified call count: 0\nRecorded call count: 2",
        )

        state.clear(
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = false,
            exclusionRules = false,
        )
        assertEquals(emptyList(), records())
        state.confirmVerified()
        assertEquals("a", state.answer(FIND_MEMBER, arrayOf(3, true)))

        state.clear(
            answers = true,
            recordedCalls = false,
            childMocks = false,
            verificationMarks = false,
            exclusionRules = false,
        )
        assertEquals(
            "no answer found for $name.find(4, true) among the configured answers: ()" + CALLED_FIND,
            assertFailsWith<LokkiException> { state.answer(FIND_MEMBER, arrayOf(4, true)) }.message,
        )
        assertEquals(listOf("$name.find(3, true)", "$name.find(4, true)"), records())
    }

    private fun find(id: Int, includeDeleted: Boolean) {
        state.answer(FIND_MEMBER, arrayOf(id, includeDeleted))
    }

    private fun records(): List<String> = state.records().map { it.toString() }

    private fun <T> start(block: suspend () -> T): () -> Result<T>? {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return { outcome }
    }

    private companion object {
        const val FIND = "sample.Repo#find(kotlin.Int,kotlin.Boolean)"
        const val SAVE = "sample.Repo#save()"
        const val EQUALS = 0
        const val HASH_CODE = 1
        const val TO_STRING = 2
        const val FIND_MEMBER = 3
        const val LABEL_MEMBER = 4
        const val CALLED_FIND = "\nCalled member: TestMock.find(Int, Boolean): String"
        const val SAVE_MEMBER = 4
    }
}
