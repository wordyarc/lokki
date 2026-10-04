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

    fun expect(state: MockState, key: String, matchers: Array<Matcher<Any?>>) {
        expectations += Expectation(state, state.descriptor.member(key), matchers)
    }

    fun check() {
        val matched = expectations.mapIndexed { index, expectation ->
            val calls = expectation.state.records()
            val matching = calls.filter(expectation::matches)
            if (matching.size > 1 && expectation.capturesIntoSlot) {
                throw LokkiException(
                    "$expectation execution is being verified more than once and its arguments are being captured " +
                        "with a slot.\nThis will store only the argument of the last invocation in the slot.\n" +
                        "If you want to store all the arguments, use a mutableList to capture arguments."
                )
            }
            val description = "call ${index + 1} of ${expectations.size}: $expectation"
            val failure = failure(description, expectation, calls, matching)
            if (failure != null) {
                if (inverse) return
                throw AssertionError("Verification failed: $failure")
            }
            matching
        }
        if (inverse) {
            val verified = matched.flatten().distinct()
            throw AssertionError("Inverse verification failed.\n\nVerified calls:\n${verified.numbered()}")
        }
        expectations.forEachIndexed { index, expectation -> matched[index].forEach(expectation::capture) }
    }

    private fun failure(
        description: String,
        expectation: Expectation,
        calls: List<CallRecord>,
        matching: List<CallRecord>,
    ): String? {
        val callsOfMember = calls.filter { it.member == expectation.member }
        return when {
            min == 0 && max == 0 -> when {
                matching.isEmpty() -> null
                else -> "$description should not be called\n\nCalls:\n${callsOfMember.numbered()}\n"
            }
            callsOfMember.isEmpty() -> when {
                min == 0 -> null
                calls.isEmpty() -> "$description was not called"
                else -> "$description was not called.\n\nCalls to same mock:\n${calls.numbered()}\n"
            }
            callsOfMember.size == 1 -> when {
                matching.isNotEmpty() -> when {
                    1 in min..max -> null
                    else -> "$description. One matching call found, but needs ${bounds()}\nCall: ${matching[0]}"
                }
                0 in min..max -> null
                else -> "$description. Only one matching call to ${expectation.method} happened, " +
                    "but arguments are not matching:\n${expectation.difference(callsOfMember[0])}"
            }
            matching.size in min..max -> null
            matching.isEmpty() ->
                "$description. No matching calls found.\n\nCalls to same method:\n${callsOfMember.numbered()}\n"
            else -> "$description. ${matching.size} matching calls found, but needs ${bounds()}\n" +
                "Calls:\n${calls.numbered()}\n"
        }
    }

    private fun bounds(): String =
        when {
            max == Int.MAX_VALUE -> "at least $min calls"
            min == max -> "exactly $min calls"
            else -> "at least $min and at most $max calls"
        }
}

private class Expectation(val state: MockState, val member: Int, private val matchers: Array<Matcher<Any?>>) {
    val capturesIntoSlot: Boolean
        get() = matchers.any { it.capturesIntoSlot }

    val method: String
        get() = "${state.name}/${state.descriptor.memberName(member)}"

    fun matches(record: CallRecord): Boolean = record.member == member && matchers.match(record.arguments)

    fun capture(record: CallRecord) {
        matchers.capture(record.arguments)
    }

    fun difference(record: CallRecord): String =
        record.arguments.withIndex().joinToString("") { (index, argument) ->
            val matcher = matchers[index]
            val result = if (matcher.match(argument)) "+" else "-"
            "[$index]: argument: ${argument.toStr()}, matcher: $matcher, result: $result\n"
        }

    override fun toString(): String = state.call(member, matchers.joinToString())
}

private fun List<CallRecord>.numbered(): String =
    withIndex().joinToString("\n") { (index, call) -> "${index + 1}) $call" }
