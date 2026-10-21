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
    private val uncalled = ArrayList<MockState>()

    init {
        val problem = when {
            exactly != -1 && (atLeast != 1 || atMost != Int.MAX_VALUE) -> "specify either atLeast/atMost or exactly"
            exactly < -1 -> "exactly should be positive"
            atLeast < 0 -> "atLeast should be positive"
            atMost < 0 -> "atMost should be positive"
            atLeast > atMost -> "atLeast should less or equal atMost"
            ordering != Ordering.UNORDERED && (atLeast != 1 || atMost != Int.MAX_VALUE || exactly != -1) ->
                "atLeast, atMost, exactly is only allowed in unordered verify block"
            else -> null
        }
        if (problem != null) throw LokkiException(problem)
    }

    @Deprecated("Kept for code compiled by older versions of the lokki compiler plugin", level = DeprecationLevel.HIDDEN)
    constructor(inverse: Boolean = false, atLeast: Int = 1, atMost: Int = Int.MAX_VALUE, exactly: Int = -1) :
        this(Ordering.UNORDERED, inverse, atLeast, atMost, exactly)

    constructor(calls: Int) : this(atLeast = calls, atMost = calls)

    constructor(calls: IntRange) : this(atLeast = calls.first, atMost = calls.last)

    fun expect(state: MockState, key: String, matchers: Array<Matcher<Any?>>) {
        expectations += Expectation(state, state.descriptor.member(key), matchers, min, max)
    }

    fun expectLink(receiver: Any?, key: String, matchers: (MockState) -> Array<Matcher<Any?>>) {
        if (ordering == Ordering.UNORDERED && (inverse || min == 0)) return
        val state = (receiver as? GeneratedMock)?.`lokki$state` ?: return
        expectations += Expectation(state, state.descriptor.member(key), matchers(state), 1, Int.MAX_VALUE)
    }

    fun expectNoCalls(mock: Any) {
        uncalled += GeneratedMock.of(mock).`lokki$state`
    }

    fun expectNoCalls(mocks: List<Any>) {
        for (mock in mocks) expectNoCalls(mock)
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
            fail("Verification failed: ${outcome.failure}", outcome.assertions)
        }
        if (timeout > 0 && uncalled.isNotEmpty()) sleep((deadline - nanoTime()) / NANOS_PER_MILLI)
        val called = uncalled.distinct().map { it to it.records() }.filter { (_, calls) -> calls.isNotEmpty() }
        if (called.isNotEmpty()) {
            if (inverse) return
            val calls = called.flatMap { (_, calls) -> calls }
            fail(
                "Verification failed: ${called.joinToString { (state, _) -> state.name }} should not be called:\n" +
                    "${calls.numbered()}\n${calls.callSites()}"
            )
        }
        val verified = outcome.matches.map { it.call }.distinct()
        if (inverse) fail("Inverse verification failed.\n\nVerified calls:\n${verified.numbered()}")
        outcome.matches.forEach { it.expectation.capture(it.call) }
        verified.groupBy { it.state }.forEach { (state, calls) -> state.markVerified(calls) }
    }

    private fun awaitCalls(deadline: Long): Outcome {
        val states = expectations.map { it.state }.distinct()
        val signal = RecordSignal()
        states.forEach { it.addWaiter(signal) }
        try {
            while (true) {
                val outcome = verifyCalls()
                if (outcome.failure == null) return outcome
                if (!signal.await(deadline)) {
                    val last = verifyCalls()
                    return if (last.failure == null) last else last.afterTimeout()
                }
            }
        } finally {
            states.forEach { it.removeWaiter(signal) }
        }
    }

    private fun verifyCalls(): Outcome =
        when (ordering) {
            Ordering.UNORDERED -> verifyUnordered()
            Ordering.ALL -> verifyAll()
            Ordering.ORDERED -> verifyOrder()
            Ordering.SEQUENCE -> verifySequence()
        }

    private fun verifyUnordered(): Outcome {
        val matches = ArrayList<Match>()
        expectations.forEachIndexed { index, expectation ->
            val outcome = expectation.verify("call ${index + 1} of ${expectations.size}: $expectation")
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
        return when {
            expectations.size > calls.size ->
                failure("fewer calls happened than demanded by order verification sequence. ", calls, lcs)
            lcs.isComplete -> Outcome(lcs.matches)
            else -> failure("calls are not in verification order", calls, lcs)
        }
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
        return failure(problem, calls, Lcs(calls, expectations))
    }

    private fun involvedCalls(): List<CallRecord> =
        expectations.map { it.state }.distinct().flatMap { it.records() }.sortedBy { it.sequenceNumber }

    private fun failure(problem: String, calls: List<CallRecord>, lcs: Lcs): Outcome {
        val matched = lcs.matches.mapTo(HashSet()) { it.expectation }
        val report = "$problem\n\nMatchers: \n" +
            expectations.joinToString("\n") { if (it in matched) "+$it" else "$it" } +
            "\n\nCalls:\n${calls.numbered(lcs.matches.mapTo(HashSet()) { it.call })}\n${calls.callSites()}"
        return Outcome(emptyList(), report, expectations.flatMap { it.assertions() })
    }

    private fun Outcome.afterTimeout(): Outcome =
        if (failure == null) this else Outcome(matches, "$failure (timeout = $timeout ms)", assertions)

    private fun fail(message: String, assertions: List<FailedAssertion> = emptyList()): Nothing {
        val states = (expectations.map { it.state } + uncalled).distinct()
        val unanswered = states.flatMap { it.unansweredRecords() }.unansweredReport()
        val error = AssertionError(unanswered + message + assertions.report())
        assertions.forEach { error.addSuppressed(it.error) }
        throw error
    }
}

private class Expectation(
    val state: MockState,
    private val member: Int,
    private val matchers: Array<Matcher<Any?>>,
    private val min: Int,
    private val max: Int,
) {
    private val evaluations = LinkedHashMap<CallRecord, Evaluation>()

    fun matches(call: CallRecord): Boolean = call.state === state && call.member == member && evaluation(call).all()

    fun verify(description: String): Outcome {
        val calls = state.records()
        val callsOfMember = calls.filter { it.member == member }
        val only = callsOfMember.singleOrNull()
        val results = only?.let { call -> evaluation(call).let { BooleanArray(matchers.size, it::get) } }
        val matching = when {
            results == null -> callsOfMember.filter { evaluation(it).all() }
            results.all { it } -> callsOfMember
            else -> emptyList()
        }
        if (matching.size > 1 && matchers.any { it.capturesIntoSlot }) {
            throw LokkiException(
                "$this execution is being verified more than once and its arguments are being captured " +
                    "with a slot.\nThis will store only the argument of the last invocation in the slot.\n" +
                    "If you want to store all the arguments, use a mutableList to capture arguments."
            )
        }
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
                else -> "$description. Only one matching call to ${state.name}/" +
                    "${state.description(member).name} happened, but arguments are not matching:\n" +
                    "${difference(only, results)}${only.callSite()}"
            }
            matching.size in min..max -> null
            matching.isEmpty() -> "$description. No matching calls found.\n\n" +
                "Calls to same method:\n${callsOfMember.numbered()}\n${callsOfMember.callSites()}"
            else -> "$description. ${matching.size} matching calls found, but needs ${bounds()}\n" +
                "Calls:\n${calls.numbered()}\n${calls.callSites()}"
        }
        val assertions = callsOfMember.flatMap { evaluations[it]?.assertions.orEmpty() }
        return Outcome(matching.map { Match(this, it) }, failure, assertions)
    }

    fun assertions(): List<FailedAssertion> = evaluations.values.flatMap { it.assertions }

    fun capture(record: CallRecord) {
        matchers.capture(record.arguments)
    }

    private fun evaluation(call: CallRecord): Evaluation = evaluations.getOrPut(call) { Evaluation(call) }

    private fun difference(record: CallRecord, results: BooleanArray): String =
        record.arguments.withIndex().joinToString("") { (index, argument) ->
            val result = if (results[index]) "+" else "-"
            "[$index]: argument: ${argument.toStr()}, matcher: ${matchers[index]}, result: $result\n"
        }

    private fun bounds(): String =
        when {
            max == Int.MAX_VALUE -> "at least $min calls"
            min == max -> "exactly $min calls"
            else -> "at least $min and at most $max calls"
        }

    override fun toString(): String = state.call(member, matchers.joinToString())

    private inner class Evaluation(private val call: CallRecord) {
        private val results = arrayOfNulls<Boolean>(matchers.size)

        val assertions = ArrayList<FailedAssertion>()

        operator fun get(index: Int): Boolean = results[index] ?: evaluate(index).also { results[index] = it }

        fun all(): Boolean = matchers.indices.all(::get)

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
)

private class FailedAssertion(val call: CallRecord, val argument: Int, val error: AssertionError)

private fun List<FailedAssertion>.report(): String {
    if (isEmpty()) return ""
    return withIndex().joinToString("\n", prefix = "\n\nAssertion errors in matchers:\n") { (index, assertion) ->
        "${index + 1}) ${assertion.call}, argument [${assertion.argument}]: ${assertion.error}"
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val DIAGONAL: Byte = 1
private const val UP: Byte = 2
private const val LEFT: Byte = 3
