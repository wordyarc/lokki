package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.Ordering

@PublishedApi
internal class Verification(
    private val ordering: Ordering = Ordering.UNORDERED,
    private val inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
    private val timeout: Long = 0,
) {
    private val min = if (exactly != -1) exactly else atLeast
    private val max = if (exactly != -1) exactly else atMost
    private val expectations = ArrayList<Expectation>()
    private val followers = ArrayList<Expectation>()
    private val uncalled = ArrayList<MockState>()
    private val absent = ArrayList<Expectation>()
    private val constructed = ArrayList<MockState>()
    private var link: Expectation? = null

    private val states: List<MockState>
        get() = expectations.flatMap { it.states }.distinct()

    init {
        val problem = when {
            exactly != -1 && (atLeast != 1 || atMost != Int.MAX_VALUE) -> "specify either atLeast/atMost or exactly"
            exactly < -1 -> "exactly should be positive"
            atLeast < 0 -> "atLeast should be positive"
            atMost < 0 -> "atMost should be positive"
            atLeast > atMost -> "atLeast should less or equal atMost"
            ordering != Ordering.UNORDERED && ordering != Ordering.ANY &&
                (atLeast != 1 || atMost != Int.MAX_VALUE || exactly != -1) ->
                "atLeast, atMost, exactly is only allowed in unordered verify block"
            else -> null
        }
        if (problem != null) throw LokkiException(problem)
    }

    constructor(calls: Int) : this(atLeast = calls, atMost = calls)

    constructor(calls: IntRange) : this(atLeast = calls.first, atMost = calls.last)

    fun expect(state: MockState, key: String, matchers: Array<Matcher<Any?>>) {
        state.checkNotShared()
        expectations += follower(Expectation(state.root, key, lazyOf(matchers), min, max, takeLink()))
    }

    fun expectLink(receiver: Any?, key: String, matchers: (MockState) -> Array<Matcher<Any?>>) {
        val origin = takeLink()
        val state = (receiver as? GeneratedMock)?.`lokki$state`()?.root ?: return
        state.checkNotShared()
        val expected = when (ordering) {
            Ordering.UNORDERED -> !inverse && min != 0
            Ordering.ANY -> false
            else -> true
        }
        val arguments = if (expected) lazyOf(matchers(state)) else lazy(LazyThreadSafetyMode.NONE) { matchers(state) }
        val link = follower(Expectation(state, key, arguments, 1, Int.MAX_VALUE, origin))
        if (expected) expectations += link
        this.link = link
    }

    fun expectNoCalls(mock: Any) {
        uncalled += GeneratedMock.of(mock).`lokki$state`().root.constructedStates().onEach { it.checkNotShared() }
    }

    fun expectNoConstructedCalls(mock: Any) {
        if (min != 0 || max != 0) {
            throw LokkiException(
                "A bare anyConstructed<T>() placeholder can only be verified with exactly = 0, " +
                    "but bounds ${boundsOf(min, max)} were requested"
            )
        }
        constructed += GeneratedMock.of(mock).`lokki$state`().root
    }

    fun expectNoCalls(mocks: List<Any>) {
        for (mock in mocks) expectNoCalls(mock)
    }

    fun expectAbsent(state: MockState, key: String, matchers: Array<Matcher<Any?>>) {
        state.checkNotShared()
        absent += Expectation(state.root, key, lazyOf(matchers), 0, 0, null)
    }

    fun check() {
        val deadline = nanoTime() + timeout * NANOS_PER_MILLI
        val outcome = when {
            timeout <= 0 -> verifyCalls()
            inverse || max != Int.MAX_VALUE -> {
                sleep(timeout)
                verifyCalls().afterTimeout()
            }
            else -> awaitCalls(deadline)
        }
        if (outcome.failure != null) {
            if (inverse) return
            fail("Verification failed: ${outcome.failure}${forwardingNote()}", outcome.assertions, outcome.thrown)
        }
        if (timeout > 0 && (uncalled.isNotEmpty() || absent.isNotEmpty() || constructed.isNotEmpty())) {
            sleep((deadline - nanoTime()) / NANOS_PER_MILLI)
        }
        val called = uncalled.distinct().map { it to it.records() }.filter { (_, calls) -> calls.isNotEmpty() }
        val present = absent.map { it to it.matchingCalls() }.filter { (_, calls) -> calls.isNotEmpty() }
        if (called.isNotEmpty() || present.isNotEmpty()) {
            if (inverse) return
            val calls = (called.flatMap { (_, calls) -> calls } + present.flatMap { (_, calls) -> calls }).distinct()
            val names = called.map { (state, _) -> state.name } + present.map { (expectation, _) -> "$expectation" }
            fail(
                "Verification failed: ${names.joinToString()} should not be called:\n" +
                    "${calls.numbered()}\n${calls.callSites()}"
            )
        }
        val constructedCalls = constructed.flatMap { it.constructedStates() }.distinct().flatMap { it.records() }
        if (constructedCalls.isNotEmpty()) {
            if (inverse) return
            val classes = constructed.distinct().joinToString { it.descriptor.name }
            fail(
                "Expected no calls on constructed instances of $classes, but ${constructedCalls.size} were found\n\n" +
                    "Calls:\n${constructedCalls.numbered()}\n${constructedCalls.callSites()}"
            )
        }
        val verified = outcome.matches.map { it.call }.distinct()
        if (inverse) fail("Inverse verification failed.\n\nVerified calls:\n${verified.numbered()}")
        outcome.matches.forEach { it.expectation.capture(it.call) }
        verified.groupBy { it.state }.forEach { (state, calls) -> state.markVerified(calls) }
    }

    private fun awaitCalls(deadline: Long): Outcome {
        val signal = RecordSignal()
        val watched = HashSet<MockState>()
        try {
            while (true) {
                follow()
                for (state in states) if (watched.add(state)) state.addWaiter(signal)
                val outcome = verifyCalls()
                if (outcome.failure == null) return outcome
                if (!signal.await(deadline)) {
                    val last = verifyCalls()
                    return if (last.failure == null) last else last.afterTimeout()
                }
            }
        } finally {
            watched.forEach { it.removeWaiter(signal) }
        }
    }

    private fun verifyCalls(): Outcome {
        follow()
        return when (ordering) {
            Ordering.UNORDERED -> verifyUnordered()
            Ordering.ALL -> verifyAll()
            Ordering.ORDERED -> verifyOrder()
            Ordering.SEQUENCE -> verifySequence()
            Ordering.ANY -> verifyAny()
        }
    }

    private fun follow() {
        followers.forEach { it.follow() }
    }

    private fun follower(expectation: Expectation): Expectation = expectation.also { if (it.follows) followers += it }

    private fun takeLink(): Expectation? = link.also { link = null }

    private fun verifyUnordered(): Outcome {
        val matches = ArrayList<Match>()
        expectations.forEachIndexed { index, expectation ->
            val outcome = expectation.verify("call ${index + 1} of ${expectations.size}: $expectation", !inverse)
            if (outcome.failure != null) return outcome
            matches += outcome.matches
        }
        return Outcome(matches)
    }

    private fun verifyAll(): Outcome {
        val outcome = verifyUnordered()
        if (outcome.failure != null) return outcome
        val calls = involvedCalls()
        val unmatched = calls.filter { call -> expectations.none { it.matches(call) } }
        if (unmatched.isEmpty()) return outcome
        return failure("some calls were not matched: $unmatched", calls, Lcs(calls, expectations))
    }

    private fun verifyOrder(): Outcome {
        val calls = involvedCalls()
        val lcs = Lcs(calls, expectations)
        val problem = when {
            expectations.size > calls.size -> "fewer calls happened than demanded by order verification sequence. "
            lcs.isComplete -> return Outcome(lcs.matches)
            else -> "calls are not in verification order"
        }
        return failure(problem, calls, lcs, divergenceNote())
    }

    private fun verifySequence(): Outcome {
        val calls = involvedCalls()
        val problem = when {
            calls.size != expectations.size ->
                "number of calls happened not matching exact number of verification sequence"
            calls.indices.any { !expectations[it].matches(calls[it]) } ->
                "calls are not exactly matching verification sequence"
            else -> return Outcome(expectations.zip(calls, ::Match))
        }
        return failure(problem, calls, Lcs(calls, expectations), divergenceNote())
    }

    private fun divergenceNote(): String {
        val diverged = states.flatMap { it.divergedRecords() }
        val expectation = expectations.firstOrNull { diverged.any(it::matches) } ?: return ""
        return "\n\nnote: $expectation had no stubbed answer / threw during execution" +
            " — this is likely where the sequence diverged"
    }

    private fun verifyAny(): Outcome {
        if (expectations.isEmpty()) return Outcome(emptyList())
        val matches = expectations.flatMap { expectation ->
            val calls = expectation.matchingCalls()
            if (calls.size > 1) expectation.checkSlots()
            calls.map { Match(expectation, it) }
        }
        val found = matches.map { it.call }.distinct()
        if (found.size in min..max) return Outcome(matches)
        val assertions = expectations.flatMap { it.assertions() }
        if (min == 0 && max == 0) {
            val report = "None of the patterns should be called, but ${found.size} matching calls found\n\n" +
                "Calls:\n${found.numbered()}\n${found.callSites()}"
            return Outcome(emptyList(), report, assertions)
        }
        val calls = involvedCalls()
        val patterns = expectations.withIndex().joinToString { (index, expectation) ->
            "call ${index + 1} of ${expectations.size}: $expectation"
        }
        val report = "${found.size} matching calls found for the declared patterns: $patterns, " +
            "but needs ${boundsOf(min, max)}\n\nCalls:\n${calls.numbered()}\n${calls.callSites()}"
        return Outcome(emptyList(), report, assertions)
    }

    private fun involvedCalls(): List<CallRecord> = states.flatMap { it.records() }.sortedBy { it.sequenceNumber }

    private fun failure(problem: String, calls: List<CallRecord>, lcs: Lcs, note: String = ""): Outcome {
        val matched = lcs.matches.mapTo(HashSet()) { it.expectation }
        val report = "$problem\n\nMatchers: \n" +
            expectations.joinToString("\n") { if (it in matched) "+$it" else "$it" } +
            "\n\nCalls:\n${calls.numbered(lcs.matches.mapTo(HashSet()) { it.call })}\n${calls.callSites()}$note"
        return Outcome(emptyList(), report, expectations.flatMap { it.assertions() })
    }

    private fun Outcome.afterTimeout(): Outcome =
        if (failure == null) this else Outcome(matches, "$failure (timeout = $timeout ms)", assertions, thrown)

    private fun forwardingNote(): String {
        val forwarding = states.filter { it.forwardsCalls }
        if (forwarding.isEmpty()) return ""
        val forward = if (forwarding.size == 1) "forwards" else "forward"
        val spies = forwarding.map { it.descriptor.name }.distinct().joinToString(" or ") { "spy($it(...))" }
        return "\n\n${forwarding.joinToString { it.name }} $forward calls to the object passed to spy(...): " +
            "calls that object makes on itself are not recorded. To record them, spy a new instance: $spies"
    }

    private fun fail(
        message: String,
        assertions: List<FailedAssertion> = emptyList(),
        thrown: List<Exception> = emptyList(),
    ): Nothing {
        val unanswered = (states + uncalled + absent.flatMap { it.states }).distinct().flatMap { it.unansweredRecords() }.unansweredReport()
        val error = AssertionError(unanswered + message + assertions.report())
        assertions.forEach { error.addSuppressed(it.error) }
        thrown.forEach(error::addSuppressed)
        throw error
    }
}

private class Expectation(
    private val state: MockState,
    private val key: String,
    matchers: Lazy<Array<Matcher<Any?>>>,
    private val min: Int,
    private val max: Int,
    private val origin: Expectation?,
) {
    private val member = state.descriptor.member(key)
    private val matchers by matchers
    private val evaluations = LinkedHashMap<CallRecord, Evaluation>()

    val follows: Boolean
        get() = origin != null || state.group != null

    var states = state.constructedStates()
        private set

    fun follow() {
        states = (state.constructedStates() + origin?.returned().orEmpty()).distinct()
    }

    fun matches(call: CallRecord): Boolean =
        call.state in states && call.member == member(call.state) && evaluation(call).all()

    fun verify(description: String, compares: Boolean): Outcome {
        val calls = records()
        val callsOfMember = calls.filter { it.member == member(it.state) }
        val only = callsOfMember.singleOrNull()
        val results = only?.let { call -> evaluation(call).let { BooleanArray(matchers.size, it::get) } }
        val matching = when {
            results == null -> callsOfMember.filter { evaluation(it).all() }
            results.all { it } -> callsOfMember
            else -> emptyList()
        }
        if (matching.size > 1) checkSlots()
        val failure = when {
            min == 0 && max == 0 -> when {
                matching.isEmpty() -> null
                else -> "$description should not be called\n\n" +
                    "Calls:\n${callsOfMember.numbered()}\n${callsOfMember.callSites()}"
            }
            callsOfMember.isEmpty() -> when {
                min == 0 -> null
                calls.isEmpty() -> "$description was not called"
                else -> "$description was not called.\n\nCalls to same mock:\n${calls.numbered()}\n${calls.callSites()}"
            }
            only != null && results != null -> when {
                matching.isNotEmpty() -> when {
                    1 in min..max -> null
                    else -> "$description. One matching call found, but needs ${bounds()}\n" +
                        "Call: $only${only.callSite()}"
                }
                0 in min..max -> null
                else -> "$description. Only one matching call to ${only.state.name}/" +
                    "${only.state.description(only.member).name} happened, but arguments are not matching:\n" +
                    "${differences(only).joinToString("") { "$it\n" }}${only.callSite()}"
            }
            matching.size in min..max -> null
            matching.isEmpty() -> "$description. No matching calls found.\n\n" +
                "Calls to same method:\n${if (compares) callsOfMember.compared() else callsOfMember.numbered()}\n" +
                callsOfMember.callSites()
            else -> "$description. ${matching.size} matching calls found, but needs ${bounds()}\n" +
                "Calls:\n${calls.numbered()}\n${calls.callSites()}"
        }
        val evaluated = callsOfMember.mapNotNull { evaluations[it] }
        return Outcome(
            matching.map { Match(this, it) },
            failure,
            evaluated.flatMap { it.assertions },
            evaluated.flatMap { it.thrown },
        )
    }

    fun matchingCalls(): List<CallRecord> = records().filter(::matches)

    fun checkSlots() {
        if (matchers.none { it.capturesIntoSlot }) return
        throw LokkiException(
            "$this execution is being verified more than once and its arguments are being captured " +
                "with a slot.\nThis will store only the argument of the last invocation in the slot.\n" +
                "If you want to store all the arguments, use a mutableList to capture arguments."
        )
    }

    fun assertions(): List<FailedAssertion> = evaluations.values.flatMap { it.assertions }

    fun capture(record: CallRecord) {
        matchers.capture(record.arguments)
    }

    private fun returned(): List<MockState> =
        records().filter { it.member == member(it.state) && evaluation(it).all() }.mapNotNull { it.result }

    private fun records(): List<CallRecord> =
        states.singleOrNull()?.records() ?: states.flatMap { it.records() }.sortedBy { it.sequenceNumber }

    private fun member(state: MockState): Int = if (state === this.state) member else state.descriptor.member(key)

    private fun evaluation(call: CallRecord): Evaluation = evaluations.getOrPut(call) { Evaluation(call) }

    private fun List<CallRecord>.compared(): String =
        withIndex().joinToString("\n") { (index, call) ->
            "${index + 1}) $call" + differences(call).joinToString("") { "\n$DIFFERENCE_INDENT$it" }
        }

    private fun differences(record: CallRecord): List<String> {
        val evaluation = evaluation(record)
        return record.arguments.indices.map { index ->
            "[$index]: argument: ${record.arguments[index].toStr()}, matcher: ${matchers[index]}, " +
                "result: ${evaluation.result(index)}"
        }
    }

    private fun bounds(): String = boundsOf(min, max)

    override fun toString(): String = state.call(member, matchers.joinToString())

    private inner class Evaluation(private val call: CallRecord) {
        private val results = arrayOfNulls<Boolean>(matchers.size)

        val assertions = ArrayList<FailedAssertion>()

        val thrown = ArrayList<Exception>()

        operator fun get(index: Int): Boolean = results[index] ?: evaluate(index).also { results[index] = it }

        fun all(): Boolean = matchers.indices.all(::get)

        fun result(index: Int): String {
            matchers[index].typeMismatch(call.arguments[index])?.let { return "- ($it)" }
            val matched = results[index] ?: try {
                get(index)
            } catch (e: Exception) {
                results[index] = false
                thrown += e
                return "- (matcher threw $e)"
            }
            return if (matched) "+" else "-"
        }

        private fun evaluate(index: Int): Boolean {
            val errors = ArrayList<AssertionError>()
            val matched = matchers[index].match(call.arguments[index], errors)
            errors.mapTo(assertions) { FailedAssertion(call, index, it) }
            return matched
        }
    }
}

private class Lcs(calls: List<CallRecord>, expectations: List<Expectation>) {
    val matches: List<Match>

    val isComplete: Boolean

    init {
        val lengths = Array(calls.size) { IntArray(expectations.size) }
        val steps = Array(calls.size) { ByteArray(expectations.size) }
        for (call in calls.indices) {
            for (expectation in expectations.indices) {
                val up = if (call == 0) 0 else lengths[call - 1][expectation]
                val left = if (expectation == 0) 0 else lengths[call][expectation - 1]
                when {
                    expectations[expectation].matches(calls[call]) -> {
                        val diagonal = if (call == 0 || expectation == 0) 0 else lengths[call - 1][expectation - 1]
                        lengths[call][expectation] = diagonal + 1
                        steps[call][expectation] = DIAGONAL
                    }
                    up > left -> {
                        lengths[call][expectation] = up
                        steps[call][expectation] = UP
                    }
                    else -> {
                        lengths[call][expectation] = left
                        steps[call][expectation] = LEFT
                    }
                }
            }
        }
        val matches = ArrayList<Match>()
        var call = calls.lastIndex
        var expectation = expectations.lastIndex
        while (call >= 0 && expectation >= 0) {
            when (steps[call][expectation]) {
                DIAGONAL -> matches += Match(expectations[expectation--], calls[call--])
                UP -> call--
                else -> expectation--
            }
        }
        this.matches = matches.asReversed()
        isComplete = matches.size == expectations.size
    }
}

private class Match(val expectation: Expectation, val call: CallRecord)

private class Outcome(
    val matches: List<Match>,
    val failure: String? = null,
    val assertions: List<FailedAssertion> = emptyList(),
    val thrown: List<Exception> = emptyList(),
)

private class FailedAssertion(val call: CallRecord, val argument: Int, val error: AssertionError)

private fun List<FailedAssertion>.report(): String {
    if (isEmpty()) return ""
    return withIndex().joinToString("\n", prefix = "\n\nAssertion errors in matchers:\n") { (index, assertion) ->
        "${index + 1}) ${assertion.call}, argument [${assertion.argument}]: ${assertion.error}"
    }
}

private fun boundsOf(min: Int, max: Int): String =
    when {
        max == Int.MAX_VALUE -> "at least $min calls"
        min == max -> "exactly $min calls"
        else -> "at least $min and at most $max calls"
    }

private const val NANOS_PER_MILLI = 1_000_000L
private const val DIFFERENCE_INDENT = "   "
private const val DIAGONAL: Byte = 1
private const val UP: Byte = 2
private const val LEFT: Byte = 3
