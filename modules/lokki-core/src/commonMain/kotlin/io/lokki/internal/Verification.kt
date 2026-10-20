package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.Matcher

@PublishedApi
internal class Verification(
    private val inverse: Boolean = false,
    atLeast: Int = 1,
    atMost: Int = Int.MAX_VALUE,
    exactly: Int = -1,
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
            else -> null
        }
        if (problem != null) throw LokkiException(problem)
    }

    constructor(calls: Int) : this(atLeast = calls, atMost = calls)

    constructor(calls: IntRange) : this(atLeast = calls.first, atMost = calls.last)

    fun expect(state: MockState, key: String, matchers: Array<Matcher<Any?>>) {
        expectations += Expectation(state, state.descriptor.member(key), matchers, min, max)
    }

    fun expectLink(receiver: Any?, key: String, matchers: (MockState) -> Array<Matcher<Any?>>) {
        if (inverse || min == 0) return
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
        val matched = expectations.mapIndexed { index, expectation ->
            val outcome = expectation.verify("call ${index + 1} of ${expectations.size}: $expectation")
            if (outcome.failure != null) {
                if (inverse) return
                fail("Verification failed: ${outcome.failure}", outcome.assertions)
            }
            outcome.matching
        }
        val called = uncalled.distinct().map { it to it.records() }.filter { (_, calls) -> calls.isNotEmpty() }
        if (called.isNotEmpty()) {
            if (inverse) return
            val calls = called.flatMap { (_, calls) -> calls }
            fail(
                "Verification failed: ${called.joinToString { (state, _) -> state.name }} should not be called:\n" +
                    "${calls.numbered()}\n${calls.callSites()}"
            )
        }
        if (inverse) fail("Inverse verification failed.\n\nVerified calls:\n${matched.flatten().distinct().numbered()}")
        expectations.forEachIndexed { index, expectation -> matched[index].forEach(expectation::capture) }
        expectations.forEachIndexed { index, expectation -> expectation.state.markVerified(matched[index]) }
    }

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
    fun verify(description: String): Outcome {
        val calls = state.records()
        val callsOfMember = calls.filter { it.member == member }
        val assertions = ArrayList<FailedAssertion>()
        val only = callsOfMember.singleOrNull()?.let { call ->
            call to BooleanArray(matchers.size) { matches(call, it, assertions) }
        }
        val matching = when {
            only == null -> callsOfMember.filter { call -> matchers.indices.all { matches(call, it, assertions) } }
            only.second.all { it } -> callsOfMember
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
            only != null -> {
                val (call, results) = only
                when {
                    matching.isNotEmpty() -> when {
                        1 in min..max -> null
                        else -> "$description. One matching call found, but needs ${bounds()}\n" +
                            "Call: $call${call.callSite()}"
                    }
                    0 in min..max -> null
                    else -> "$description. Only one matching call to ${state.name}/" +
                        "${state.description(member).name} happened, but arguments are not matching:\n" +
                        "${difference(call, results)}${call.callSite()}"
                }
            }
            matching.size in min..max -> null
            matching.isEmpty() -> "$description. No matching calls found.\n\n" +
                "Calls to same method:\n${callsOfMember.numbered()}\n${callsOfMember.callSites()}"
            else -> "$description. ${matching.size} matching calls found, but needs ${bounds()}\n" +
                "Calls:\n${calls.numbered()}\n${calls.callSites()}"
        }
        return Outcome(matching, failure, assertions)
    }

    fun capture(record: CallRecord) {
        matchers.capture(record.arguments)
    }

    private fun matches(call: CallRecord, index: Int, assertions: MutableList<FailedAssertion>): Boolean {
        val errors = ArrayList<AssertionError>()
        val matched = matchers[index].match(call.arguments[index], errors)
        errors.mapTo(assertions) { FailedAssertion(call, index, it) }
        return matched
    }

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
}

private class Outcome(val matching: List<CallRecord>, val failure: String?, val assertions: List<FailedAssertion>)

private class FailedAssertion(val call: CallRecord, val argument: Int, val error: AssertionError)

private fun List<FailedAssertion>.report(): String {
    if (isEmpty()) return ""
    return withIndex().joinToString("\n", prefix = "\n\nAssertion errors in matchers:\n") { (index, assertion) ->
        "${index + 1}) ${assertion.call}, argument [${assertion.argument}]: ${assertion.error}"
    }
}
