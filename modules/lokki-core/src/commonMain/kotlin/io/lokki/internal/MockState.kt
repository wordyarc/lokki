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
internal class MockState(private val owner: Any, spec: MockSpec, internal val descriptor: MockDescriptor) {
    internal val name = "${descriptor.name}(${spec.name.orEmpty()}#${MockIds.next()})"

    @Volatile
    private var stubs = arrayOfNulls<Array<StubEntry>>(descriptor.memberCount)

    private val records = ArrayList<CallRecord>()

    @Volatile
    private var exclusions = emptyArray<Exclusion>()

    @Volatile
    private var capture: DefaultsCapture? = null

    fun answer(member: Int, arguments: Array<Any?>): Any? {
        val capture = capture
        if (capture != null && capture.member == member && capture.thread === currentThread()) {
            capture.arguments = arguments
            throw DefaultsCaptured()
        }
        val changesState = !Registration.isActive
        val record = if (changesState && member > TO_STRING) record(member, arguments) else null
        val stub = findStub(member, arguments) ?: return defaultAnswer(member, arguments, record)
        if (changesState) stub.use(arguments)
        return when (val answer = stub.answer(advance = changesState)) {
            is ConstantAnswer -> answer.value
            is ThrowingAnswer -> throw answer.exception
            is ArgumentAnswer -> arguments[answer.index]
            else -> answer.answer(Call(Invocation(owner, ArgumentList(arguments))))
        }
    }

    fun <T> stub(key: String, matchers: Array<Matcher<Any?>>): StubScope<T> =
        PendingStub(this, descriptor.member(key), matchers)

    fun exclude(key: String, matchers: Array<Matcher<Any?>>, current: Boolean = true) {
        val member = descriptor.member(key)
        if (member <= TO_STRING) return
        val exclusion = Exclusion(member, matchers)
        synchronized(this) { exclusions += exclusion }
        if (current) synchronized(records) { records.removeAll { exclusion.matches(it.member, it.arguments) } }
    }

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

    internal fun memberName(member: Int): String = "${descriptor.name}.${descriptor.memberName(member)}"

    internal fun register(member: Int, entry: StubEntry) {
        synchronized(this) {
            val stubs = stubs.copyOf()
            stubs[member] = stubs[member]?.plus(entry) ?: arrayOf(entry)
            this.stubs = stubs
        }
    }

    internal fun records(): List<CallRecord> = synchronized(records) { records.toList() }

    internal fun unansweredRecords(): List<CallRecord> = synchronized(records) { records.filter { it.unanswered } }

    internal fun markVerified(calls: List<CallRecord>) {
        synchronized(records) { calls.forEach { it.verified = true } }
    }

    internal fun confirmVerified() {
        val (recorded, unverified) = synchronized(records) { records.size to records.filterNot { it.verified } }
        if (unverified.isEmpty()) return
        throw AssertionError(
            unansweredRecords().unansweredReport() +
                "Verification acknowledgment failed\n\n" +
                "Verified call count: ${recorded - unverified.size}\nRecorded call count: $recorded\n\n\n" +
                "Not verified calls:\n${unverified.numbered()}\n${unverified.callSites()}"
        )
    }

    internal fun checkUnnecessaryStub() {
        val unused = describeStubs { !it.used }
        if (unused.isEmpty()) return
        throw AssertionError(
            "Unnecessary stubbings detected.\nFollowing stubbings are not used, either because there are " +
                "unnecessary or because tested code doesn't call them :\n\n" +
                unused.withIndex().joinToString("\n") { (index, stub) -> "${index + 1}) $stub" }
        )
    }

    internal fun clear(answers: Boolean, recordedCalls: Boolean, verificationMarks: Boolean, exclusionRules: Boolean) {
        synchronized(this) {
            if (answers) stubs = arrayOfNulls(descriptor.memberCount)
            if (exclusionRules) exclusions = emptyArray()
        }
        synchronized(records) {
            if (recordedCalls) records.clear()
            if (verificationMarks) records.forEach { it.verified = false }
        }
    }

    internal fun call(member: Int, arguments: String): String = "$name.${descriptor.memberName(member)}($arguments)"

    private fun record(member: Int, arguments: Array<Any?>): CallRecord? {
        val exclusions = exclusions
        if (exclusions.isNotEmpty() && exclusions.any { it.matches(member, arguments) }) return null
        val callStack = captureCallStack()
        return synchronized(records) {
            CallRecord(this, member, arguments, nextSequenceNumber(), callStack).also { records += it }
        }
    }

    private fun captureArguments(member: Int, call: (Boolean) -> Unit, alternate: Boolean): Array<Any?> {
        val capture = DefaultsCapture(member, currentThread())
        this.capture = capture
        try {
            call(alternate)
        } catch (_: DefaultsCaptured) {
        } catch (e: Exception) {
            throw LokkiException(
                "default values of ${memberName(member)} cannot be evaluated; pass them explicitly or use any()",
                e,
            )
        } finally {
            this.capture = null
        }
        return checkNotNull(capture.arguments) { "default arguments of member $member were not captured" }
    }

    private fun defaultAnswer(member: Int, arguments: Array<Any?>, record: CallRecord?): Any? =
        when (member) {
            EQUALS -> owner === arguments[0]
            HASH_CODE -> identityHashCode(owner)
            TO_STRING -> name
            else -> {
                if (record != null) synchronized(records) { record.unanswered = true }
                throw LokkiException(
                    "no answer found for ${call(member, arguments.joinToString { it.toStr() })} " +
                        "among the configured answers: (${describeStubs { true }.joinToString("\n")})"
                )
            }
        }

    private inline fun describeStubs(predicate: (StubEntry) -> Boolean): List<String> {
        val stubs = stubs
        return stubs.indices.flatMap { member ->
            stubs[member].orEmpty().filter(predicate).map { call(member, it.arguments) }
        }
    }

    private fun findStub(member: Int, arguments: Array<Any?>): StubEntry? {
        val entries = stubs[member] ?: return null
        for (index in entries.lastIndex downTo 0) {
            val entry = entries[index]
            if (entry.matches(arguments)) return entry
        }
        return null
    }
}

internal class CallRecord(
    private val state: MockState,
    val member: Int,
    val arguments: Array<Any?>,
    val sequenceNumber: Long,
    val callStack: Throwable?,
) {
    var verified = false

    var unanswered = false

    override fun toString(): String = state.call(member, arguments.joinToString { it.toStr() })
}

internal class StubEntry(private val matchers: Array<Matcher<Any?>>, answers: List<Answer<Any?>>) {
    private val captures = matchers.any { it is CapturingMatcher }

    @Volatile
    private var answers = answers.toTypedArray()

    private var position = 0

    @Volatile
    var used = false
        private set

    val arguments: String
        get() = matchers.joinToString()

    fun matches(arguments: Array<Any?>): Boolean = matchers.match(arguments)

    fun use(arguments: Array<Any?>) {
        if (!used) used = true
        if (captures) matchers.capture(arguments)
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

    override fun returnsArgument(n: Int): AdditionalAnswerScope<T> {
        if (n !in matchers.indices) {
            throw LokkiException(
                "returnsArgument($n) is out of range: " +
                    "the number of arguments of ${state.memberName(member)} is ${matchers.size}"
            )
        }
        return add(listOf(ArgumentAnswer(n)))
    }

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

private class Exclusion(private val member: Int, private val matchers: Array<Matcher<Any?>>) {
    fun matches(member: Int, arguments: Array<Any?>): Boolean = member == this.member && matchers.match(arguments)
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
