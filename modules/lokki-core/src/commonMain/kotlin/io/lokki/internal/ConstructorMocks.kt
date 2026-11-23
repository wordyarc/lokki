package io.lokki.internal

import io.lokki.LokkiScope
import io.lokki.Matcher

@PublishedApi
internal class ConstructorMocks internal constructor(
    private val className: String,
    private val factory: (MockSpec) -> Any,
    private val scope: LokkiScope,
) {
    private val constructed = ArrayList<Constructed>()
    private val selected = ArrayList<Selected>()

    internal val any: GeneratedMock = representative("")

    init {
        any.`lokki$state`.group = this
    }

    internal val states: List<MockState>
        get() = synchronized(constructed) {
            buildList {
                add(any.`lokki$state`)
                selected.mapTo(this) { it.state }
                constructed.mapTo(this) { it.state }
            }
        }

    fun spec(arguments: Array<Any?>): MockSpec {
        val state = synchronized(constructed) {
            constructed.firstOrNull { it.hasArguments(arguments) } ?: Constructed(arguments).also {
                constructed += it
                it.linkStubs()
            }
        }.state
        return MockSpec(null, SPY, state)
    }

    internal fun representative(matchers: Array<Matcher<Any?>>): GeneratedMock {
        if (matchers.isEmpty()) return any
        return synchronized(constructed) {
            selected.firstOrNull { it.hasMatchers(matchers) } ?: Selected(matchers).also {
                it.state.group = this
                selected += it
                constructed.forEach { constructed -> constructed.linkStubs() }
            }
        }.mock
    }

    internal fun statesOf(state: MockState): List<MockState> {
        if (state === any.`lokki$state`) return states
        return synchronized(constructed) {
            val owner = selected.first { it.state === state }
            listOf(state) + constructed.filter { owner.matches(it.arguments) }.map { it.state }
        }
    }

    internal fun clear(answers: Boolean, recordedCalls: Boolean, childMocks: Boolean) {
        for (state in states) {
            state.clear(
                answers,
                recordedCalls,
                childMocks,
                verificationMarks = true,
                exclusionRules = true,
            )
        }
    }

    internal fun detach() {
        states.forEach { it.detach() }
    }

    private fun representative(arguments: String): GeneratedMock =
        LokkiScope.withCurrent(scope) {
            factory(MockSpec("mockConstructor<$className>($arguments)", CONSTRUCTOR)) as GeneratedMock
        }

    private fun Constructed.linkStubs() {
        state.fallBackTo(selected.filter { it.matches(arguments) }.map { it.state } + any.`lokki$state`)
    }

    private inner class Constructed(val arguments: Array<Any?>) {
        private val matchers = equalTo(arguments)
        val state: MockState = representative(arguments.joinToString { "eq(${it.toStr()})" }).`lokki$state`

        fun hasArguments(other: Array<Any?>): Boolean = matchers.size == other.size && matchers.match(other)
    }

    private inner class Selected(private val matchers: Array<Matcher<Any?>>) {
        val mock: GeneratedMock = representative(matchers.joinToString())
        val state: MockState
            get() = mock.`lokki$state`

        fun matches(arguments: Array<Any?>): Boolean = matchers.size == arguments.size && matchers.match(arguments)

        fun hasMatchers(other: Array<Matcher<Any?>>): Boolean =
            matchers.size == other.size && matchers.indices.all { matchers[it].isSameAs(other[it]) }
    }
}
