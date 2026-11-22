package io.lokki.internal

import io.lokki.AdditionalAnswerScope
import io.lokki.Answer
import io.lokki.AnswerScope
import io.lokki.Call
import io.lokki.Invocation
import io.lokki.InvocationMatcher
import io.lokki.LokkiException
import io.lokki.LokkiScope
import io.lokki.Matcher
import io.lokki.MethodDescription
import io.lokki.StubScope
import kotlin.concurrent.Volatile
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@PublishedApi
internal class MockState(private val owner: Any, spec: MockSpec, internal val descriptor: MockDescriptor) {
    private val label = "${spec.name.orEmpty()}#${MockIds.next()}"

    internal val name = "${descriptor.name}($label)"

    private val flags = spec.flags

    internal val isObject: Boolean
        get() = flags and OBJECT != 0

    internal val isStatic: Boolean
        get() = flags and STATIC != 0

    @Volatile
    private var stubs = arrayOfNulls<Array<StubEntry>>(descriptor.memberCount)

    private val records = ArrayList<CallRecord>()

    @Volatile
    private var waiters = emptyArray<RecordSignal>()

    @Volatile
    private var exclusions = emptyArray<Exclusion>()

    @Volatile
    private var capture: DefaultsCapture? = null

    private val children = ArrayList<Child>()

    internal val scope: LokkiScope? = LokkiScope.owner()

    init {
        if (descriptor.abi > LokkiAbi.VERSION) {
            throw LokkiException(
                "the mock of ${descriptor.name} was compiled by a newer lokki compiler plugin than lokki-core on " +
                    "the classpath: it needs ABI ${descriptor.abi}, lokki-core has ABI ${LokkiAbi.VERSION}; " +
                    "use the version of the lokki Gradle plugin for all lokki dependencies"
            )
        }
        scope?.adopt(this)
    }

    fun answer(member: Int, arguments: Array<Any?>): Any? =
        respond(
            member,
            arguments,
            { record -> returned(record, mock.`lokki$original`(member, arguments)) },
        ) { answer, call, record -> returned(record, answer.answer(call)) }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> coAnswer(member: Int, arguments: Array<Any?>): T =
        respond(member, arguments, { mock.`lokki$coOriginal`(member, arguments) }) { answer, call, record ->
            if (record == null) answer.coAnswer(call) else coReturned(record, answer, call)
        } as T

    fun noOriginal(member: Int, arguments: Array<Any?>): Any? {
        if (flags and SPY != 0 && description(member).returnsUnit) return Unit
        throw LokkiException("no original implementation for ${call(member, arguments.joinToString { it.toStr() })}")
    }

    fun <T> stub(key: String, matchers: Array<Matcher<Any?>>): StubScope<T> {
        checkNotShared()
        return PendingStub(this, descriptor.member(key), matchers)
    }

    fun link(key: String, matchers: Array<Matcher<Any?>>, stubs: Boolean, factory: (MockSpec) -> Any): Any {
        checkNotShared()
        return linkedChild(descriptor.member(key), matchers, stubs, factory)
    }

    fun exclude(key: String, matchers: Array<Matcher<Any?>>, current: Boolean = true) {
        val member = descriptor.member(key)
        if (member <= TO_STRING) return
        checkNotShared()
        val exclusion = Exclusion(member, matchers, nextMark())
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

    fun coCaptureDefaults(key: String, names: Array<String?>, call: suspend (Boolean) -> Unit): Array<Any?> =
        captureDefaults(key, names) { alternate -> call.startCoroutineUninterceptedOrReturn(alternate, Abandoned) }

    internal fun memberName(member: Int): String = "${descriptor.name}.${description(member).name}"

    internal fun description(member: Int): MethodDescription = descriptor.description(mock, member)

    internal fun register(member: Int, entry: StubEntry) {
        synchronized(this) {
            val stubs = stubs.copyOf()
            stubs[member] = stubs[member]?.plus(entry) ?: arrayOf(entry)
            this.stubs = stubs
        }
    }

    internal fun records(since: Long = ALL): List<CallRecord> =
        synchronized(records) { records.filter { it.sequenceNumber > since } }

    internal fun unansweredRecords(since: Long = ALL): List<CallRecord> =
        synchronized(records) { records.filter { it.unanswered && it.sequenceNumber > since } }

    internal fun checkNotShared() {
        scope?.checkNotShared(name)
    }

    internal fun addWaiter(signal: RecordSignal) {
        synchronized(records) { waiters += signal }
    }

    internal fun removeWaiter(signal: RecordSignal) {
        synchronized(records) { waiters = waiters.filter { it !== signal }.toTypedArray() }
    }

    internal fun markVerified(calls: List<CallRecord>) {
        synchronized(records) { calls.forEach { it.verified = true } }
    }

    internal fun confirmVerified(since: Long = ALL) {
        val recorded = records(since)
        val unverified = synchronized(records) { recorded.filterNot { it.verified } }
        if (unverified.isEmpty()) return
        checkNotShared()
        throw AssertionError(
            unansweredRecords(since).unansweredReport() +
                "Verification acknowledgment failed\n\n" +
                "Verified call count: ${recorded.size - unverified.size}\nRecorded call count: ${recorded.size}\n\n\n" +
                "Not verified calls:\n${unverified.numbered()}\n${unverified.callSites()}"
        )
    }

    internal fun checkUnnecessaryStub(since: Long = ALL) {
        val unused = describeStubs { !it.used && it.mark > since }
        if (unused.isEmpty()) return
        throw AssertionError(
            "Unnecessary stubbings detected.\nFollowing stubbings are not used, either because there are " +
                "unnecessary or because tested code doesn't call them :\n\n" +
                unused.withIndex().joinToString("\n") { (index, stub) -> "${index + 1}) $stub" }
        )
    }

    internal fun clear(
        answers: Boolean,
        recordedCalls: Boolean,
        childMocks: Boolean,
        verificationMarks: Boolean,
        exclusionRules: Boolean,
        since: Long = ALL,
    ) {
        synchronized(this) {
            if (answers) {
                stubs = stubs.map { entries ->
                    entries?.filter { it.mark <= since }?.takeIf { it.isNotEmpty() }?.toTypedArray()
                }.toTypedArray()
            }
            if (childMocks) children.removeAll { it.mark > since }
            if (exclusionRules) exclusions = exclusions.filter { it.mark <= since }.toTypedArray()
        }
        synchronized(records) {
            if (recordedCalls) records.removeAll { it.sequenceNumber > since }
            if (verificationMarks) records.forEach { if (it.sequenceNumber > since) it.verified = false }
        }
    }

    internal fun chainPositions(): List<StubPosition> =
        stubs.flatMap { entries -> entries.orEmpty().mapNotNull { it.chainPosition() } }

    internal fun call(member: Int, arguments: String): String = "$name.${description(member).name}($arguments)"

    private val mock: GeneratedMock
        get() = owner as GeneratedMock

    private inline fun respond(
        member: Int,
        arguments: Array<Any?>,
        original: (CallRecord?) -> Any?,
        answer: (Answer<Any?>, Call, CallRecord?) -> Any?,
    ): Any? {
        val capture = capture
        if (capture != null && capture.member == member && capture.thread === currentThread()) {
            capture.arguments = arguments
            throw DefaultsCaptured()
        }
        val changesState = !Registration.isActive
        val link = if (changesState) null else Registration.takeLink(this, member)
        val record = if (changesState && member > TO_STRING) record(member, arguments) else null
        val stub = findStub(member, arguments) ?: return when {
            answersWithOriginal(member) -> original(record)
            link?.factory != null -> linkedChild(member, equalTo(arguments), link.stubs, link.factory)
            else -> defaultAnswer(member, arguments, record, link)
        }
        if (changesState) stub.use(arguments)
        return when (val selected = stub.answer(advance = changesState)) {
            is ConstantAnswer -> {
                val state = selected.state
                if (record != null && state != null) returnedMock(record, state)
                selected.value
            }
            is ThrowingAnswer -> throw selected.exception
            is ArgumentAnswer -> returned(record, arguments[selected.index])
            else -> answer(selected, Call(Invocation(owner, member, arguments), stub), record)
        }
    }

    private suspend fun coReturned(record: CallRecord, answer: Answer<Any?>, call: Call): Any? =
        returned(record, answer.coAnswer(call))

    private fun returned(record: CallRecord?, value: Any?): Any? {
        if (record != null && value is GeneratedMock) returnedMock(record, value.`lokki$state`)
        return value
    }

    private fun returnedMock(record: CallRecord, state: MockState) {
        record.result = state
        for (waiter in waiters) waiter.signal()
    }

    private fun record(member: Int, arguments: Array<Any?>): CallRecord? {
        val exclusions = exclusions
        if (exclusions.isNotEmpty() && exclusions.any { it.matches(member, arguments) }) return null
        val callStack = captureCallStack()
        return synchronized(records) {
            val record = CallRecord(this, member, arguments, nextSequenceNumber(), callStack)
            records += record
            for (waiter in waiters) waiter.signal()
            record
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

    private fun defaultAnswer(member: Int, arguments: Array<Any?>, record: CallRecord?, link: Link?): Any? {
        when (member) {
            EQUALS -> return owner === arguments[0]
            HASH_CODE -> return identityHashCode(owner)
            TO_STRING -> return name
        }
        val message = if (flags and RELAXED != 0) {
            val value = relaxedAnswer(member)
            if (value is ChildMock) return returned(record, child(member, equalTo(arguments), value.factory))
            if (value !is NoRelaxedDefault) return value
            val hint = if (value.mockable && link == null) RELAXED_CHILD_HINT else ""
            "relaxed mock has no default for ${value.type} returned by " +
                "${call(member, arguments.joinToString { it.toStr() })}; stub the call$hint"
        } else {
            if (flags and RELAX_UNIT_FUN != 0 && relaxedAnswer(member) === Unit) return Unit
            "no answer found for ${call(member, arguments.joinToString { it.toStr() })} " +
                "among the configured answers: (${describeStubs { true }.joinToString("\n")})\n" +
                "Called member: ${description(member).signature()}"
        }
        val hint = link?.let { "\n$LINK_HINT: stub it first, a child mock cannot be created: ${it.problem}" }.orEmpty()
        val closed = scope?.takeIf { it.isClosed }
            ?.let { "\nThe mock belongs to $it, which is closed: the call came after its end" }
            .orEmpty()
        val failure = LokkiException(message + hint + closed)
        if (record != null) synchronized(records) { record.failure = failure }
        throw failure
    }

    private fun linkedChild(
        member: Int,
        matchers: Array<Matcher<Any?>>,
        registers: Boolean,
        factory: (MockSpec) -> Any,
    ): Any {
        val child = child(member, matchers, factory)
        if (!registers) return child
        synchronized(this) {
            if (stubs[member]?.lastOrNull()?.answersWith(child, matchers) != true) {
                register(member, StubEntry(matchers, listOf(ConstantAnswer(child)), nextMark()))
            }
        }
        return child
    }

    private fun child(member: Int, matchers: Array<Matcher<Any?>>, factory: (MockSpec) -> Any): Any =
        synchronized(this) {
            children.find { it.member == member && it.matchers.isEquivalentTo(matchers) }?.mock
                ?: factory(MockSpec(childName(), flags and (RELAXED or RELAX_UNIT_FUN)))
                    .also { children += Child(member, matchers, it, nextMark()) }
        }

    private fun childName(): String {
        val chained = CHILD_NAME.matchEntire(label) ?: return "child of $label"
        val depth = chained.groupValues[2].ifEmpty { "1" }.toInt()
        return "child^${depth + 1} of ${chained.groupValues[3]}"
    }

    private fun answersWithOriginal(member: Int): Boolean =
        flags and SPY != 0 && member != TO_STRING && (member > TO_STRING || flags and DELEGATE == 0)

    private fun relaxedAnswer(member: Int): Any? = mock.`lokki$relaxed`(member)

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
    val state: MockState,
    val member: Int,
    val arguments: Array<Any?>,
    val sequenceNumber: Long,
    val callStack: Throwable?,
) {
    var verified = false

    var failure: LokkiException? = null

    @Volatile
    var result: MockState? = null

    val unanswered: Boolean
        get() = failure != null

    override fun toString(): String = state.call(member, arguments.joinToString { it.toStr() })
}

internal class StubEntry(
    private val matchers: Array<Matcher<Any?>>,
    answers: List<Answer<Any?>>,
    val mark: Long,
) {
    private val captures = matchers.any { it is CapturingMatcher }

    val lambda = matchers.indexOfLast { it.capturesLambda }

    @Volatile
    private var answers = answers.toTypedArray()

    private var position = 0

    @Volatile
    var used = false
        private set

    @Volatile
    private var invocationMatcher: InvocationMatcher? = null

    val arguments: String
        get() = matchers.joinToString()

    fun invocationMatcher(self: Any, member: Int): InvocationMatcher =
        invocationMatcher ?: InvocationMatcher(self, member, matchers).also { invocationMatcher = it }

    fun matches(arguments: Array<Any?>): Boolean = matchers.match(arguments)

    fun answersWith(value: Any, matchers: Array<Matcher<Any?>>): Boolean {
        val answers = answers
        return answers.size == 1 && (answers[0] as? ConstantAnswer)?.value === value &&
            this.matchers.isEquivalentTo(matchers)
    }

    fun use(arguments: Array<Any?>) {
        if (!used) used = true
        if (captures) matchers.capture(arguments)
    }

    fun add(answers: List<Answer<Any?>>) {
        synchronized(this) { this.answers += answers }
    }

    fun chainPosition(): StubPosition? =
        synchronized(this) { if (answers.size > 1) StubPosition(this, position) else null }

    fun restore(position: Int) {
        synchronized(this) { this.position = position }
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

    override fun coAnswers(answer: suspend AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T> =
        add(listOf(SuspendingAnswer { call -> AnswerScope<T>(call).answer(call) }))

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

    override fun coAndThen(answer: suspend AnswerScope<T>.(Call) -> T): AdditionalAnswerScope<T> = coAnswers(answer)

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
            else -> this.entry = StubEntry(matchers, answers, nextMark()).also { state.register(member, it) }
        }
        return this
    }
}

internal class StubPosition(private val entry: StubEntry, private val position: Int) {
    fun restore() {
        entry.restore(position)
    }
}

private class Child(val member: Int, val matchers: Array<Matcher<Any?>>, val mock: Any, val mark: Long)

private class Exclusion(private val member: Int, private val matchers: Array<Matcher<Any?>>, val mark: Long) {
    fun matches(member: Int, arguments: Array<Any?>): Boolean = member == this.member && matchers.match(arguments)
}

private class DefaultsCapture(val member: Int, val thread: Any) {
    var arguments: Array<Any?>? = null
}

private class DefaultsCaptured : RuntimeException()

private object MockIds {
    private var last = 0

    fun next(): Int = synchronized(this) { ++last }
}

internal const val ALL = -1L

private const val EQUALS = 0
private const val HASH_CODE = 1
private const val TO_STRING = 2
private const val LINK_HINT = "The call is a link of a call chain in a stubbed or verified call"
private const val CHAINED_MOCKS = "@file:ChainedMocks"
private const val RELAXED_CHILD_HINT = " or annotate the file that creates the mock with $CHAINED_MOCKS"
private val CHILD_NAME = Regex("child(\\^(\\d+))? of (.+)")
