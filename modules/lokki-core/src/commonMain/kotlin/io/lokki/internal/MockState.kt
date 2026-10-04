package io.lokki.internal

import io.lokki.AdditionalAnswerScope
import io.lokki.Answer
import io.lokki.AnswerScope
import io.lokki.Call
import io.lokki.Invocation
import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.StubScope
import kotlin.concurrent.Volatile

@PublishedApi
internal class MockState(private val owner: Any, spec: MockSpec, private val descriptor: MockDescriptor) {
    private val name = "${descriptor.name}(${spec.name.orEmpty()}#${MockIds.next()})"

    @Volatile
    private var stubs = arrayOfNulls<Array<StubEntry>>(descriptor.memberCount)

    @Volatile
    private var capture: DefaultsCapture? = null

    fun answer(member: Int, arguments: Array<Any?>): Any? {
        val capture = capture
        if (capture != null && capture.member == member && capture.thread === currentThread()) {
            capture.arguments = arguments
            throw DefaultsCaptured()
        }
        val stub = findStub(member, arguments) ?: return defaultAnswer(member, arguments)
        val changesState = !Registration.isActive
        if (changesState) stub.capture(arguments)
        return when (val answer = stub.answer(advance = changesState)) {
            is ConstantAnswer -> answer.value
            is ThrowingAnswer -> throw answer.exception
            is ArgumentAnswer -> arguments[answer.index]
            else -> answer.answer(Call(Invocation(owner, ArgumentList(arguments))))
        }
    }

    fun <T> stub(key: String, matchers: Array<Matcher<Any?>>): StubScope<T> =
        PendingStub(this, descriptor.member(key), matchers)

    fun captureDefaults(key: String, names: Array<String?>, call: (Boolean) -> Unit): Array<Any?> =
        synchronized(this) {
            val member = descriptor.member(key)
            val defaults = captureArguments(member, call, alternate = false)
            val alternate = captureArguments(member, call, alternate = true)
            names.forEachIndexed { index, name ->
                if (name != null && !deepEquals(defaults[index], alternate[index])) {
                    throw LokkiException(
                        "default value of '$name' is unstable, has no equals, or depends on a matcher argument; " +
                            "pass it explicitly or use any()"
                    )
                }
            }
            defaults
        }

    internal fun register(member: Int, entry: StubEntry) {
        synchronized(this) {
            val stubs = stubs.copyOf()
            stubs[member] = stubs[member]?.plus(entry) ?: arrayOf(entry)
            this.stubs = stubs
        }
    }

    private fun captureArguments(member: Int, call: (Boolean) -> Unit, alternate: Boolean): Array<Any?> {
        val capture = DefaultsCapture(member, currentThread())
        this.capture = capture
        try {
            call(alternate)
        } catch (_: DefaultsCaptured) {
        } catch (e: Exception) {
            val name = "${descriptor.name}.${descriptor.memberName(member)}"
            throw LokkiException("default values of $name cannot be evaluated; pass them explicitly or use any()", e)
        } finally {
            this.capture = null
        }
        return checkNotNull(capture.arguments) { "default arguments of member $member were not captured" }
    }

    private fun defaultAnswer(member: Int, arguments: Array<Any?>): Any? =
        when (member) {
            EQUALS -> owner === arguments[0]
            HASH_CODE -> identityHashCode(owner)
            TO_STRING -> name
            else -> throw LokkiException(
                "no answer found for ${call(member, arguments.joinToString { it.toStr() })} " +
                    "among the configured answers: (${configuredAnswers()})"
            )
        }

    private fun configuredAnswers(): String {
        val stubs = stubs
        return stubs.indices
            .flatMap { member -> stubs[member].orEmpty().map { call(member, it.arguments) } }
            .joinToString("\n")
    }

    private fun call(member: Int, arguments: String): String = "$name.${descriptor.memberName(member)}($arguments)"

    private fun findStub(member: Int, arguments: Array<Any?>): StubEntry? {
        val entries = stubs[member] ?: return null
        for (index in entries.lastIndex downTo 0) {
            val entry = entries[index]
            if (entry.matches(arguments)) return entry
        }
        return null
    }
}

internal class StubEntry(private val matchers: Array<Matcher<Any?>>, answers: List<Answer<Any?>>) {
    private val captures = matchers.any { it is CapturingMatcher }

    @Volatile
    private var answers = answers.toTypedArray()

    private var position = 0

    val arguments: String
        get() = matchers.joinToString()

    fun matches(arguments: Array<Any?>): Boolean {
        for (index in matchers.indices) {
            if (!matchers[index].match(arguments[index])) return false
        }
        return true
    }

    fun capture(arguments: Array<Any?>) {
        if (!captures) return
        for (index in matchers.indices) {
            (matchers[index] as? CapturingMatcher)?.capture(arguments[index])
        }
    }

    fun add(answers: List<Answer<Any?>>) {
        synchronized(this) { this.answers += answers }
    }

    fun answer(advance: Boolean): Answer<Any?> {
        val answers = answers
        if (answers.size == 1) return answers[0]
        return synchronized(this) {
            val current = this.answers
            val answer = current[position]
            if (advance && position < current.lastIndex) position++
            answer
        }
    }
}

private class PendingStub<T>(
    private val state: MockState,
    private val member: Int,
    private val matchers: Array<Matcher<Any?>>,
) : StubScope<T>, AdditionalAnswerScope<T> {
    private var entry: StubEntry? = null

    override fun answers(answer: Answer<T>): AdditionalAnswerScope<T> = add(listOf(answer))

    override fun answers(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T> =
        add(listOf(Answer { call -> AnswerScope<T>(call).answer(call) }))

    override fun returns(returnValue: T): AdditionalAnswerScope<T> = add(listOf(ConstantAnswer(returnValue)))

    override fun returnsMany(values: List<T>): AdditionalAnswerScope<T> = add(values.map(::ConstantAnswer))

    override fun returnsMany(vararg values: T): AdditionalAnswerScope<T> = returnsMany(values.asList())

    override fun returnsArgument(n: Int): AdditionalAnswerScope<T> = add(listOf(ArgumentAnswer(n)))

    override fun throws(ex: Throwable): AdditionalAnswerScope<T> = add(listOf(ThrowingAnswer(ex)))

    override fun throwsMany(exList: List<Throwable>): AdditionalAnswerScope<T> = add(exList.map(::ThrowingAnswer))

    override fun andThenAnswer(answer: Answer<T>): AdditionalAnswerScope<T> = answers(answer)

    override fun andThenAnswer(answer: AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T> = answers(answer)

    override fun andThen(returnValue: T): AdditionalAnswerScope<T> = returns(returnValue)

    override fun andThenMany(values: List<T>): AdditionalAnswerScope<T> = returnsMany(values)

    override fun andThenMany(vararg values: T): AdditionalAnswerScope<T> = returnsMany(values.asList())

    override fun andThenThrows(ex: Throwable): AdditionalAnswerScope<T> = throws(ex)

    override fun andThenThrowsMany(exList: List<Throwable>): AdditionalAnswerScope<T> = throwsMany(exList)

    private fun add(answers: List<Answer<Any?>>): AdditionalAnswerScope<T> {
        val entry = entry
        when {
            entry != null -> entry.add(answers)
            answers.isEmpty() -> throw LokkiException("a stub needs at least one answer")
            else -> this.entry = StubEntry(matchers, answers).also { state.register(member, it) }
        }
        return this
    }
}

private class ArgumentList(private val arguments: Array<Any?>) : AbstractList<Any?>() {
    override val size: Int
        get() = arguments.size

    override fun get(index: Int): Any? = arguments[index]
}

private class DefaultsCapture(val member: Int, val thread: Any) {
    var arguments: Array<Any?>? = null
}

private class DefaultsCaptured : RuntimeException()

private object MockIds {
    private var last = 0

    fun next(): Int = synchronized(this) { ++last }
}

private const val EQUALS = 0
private const val HASH_CODE = 1
private const val TO_STRING = 2
