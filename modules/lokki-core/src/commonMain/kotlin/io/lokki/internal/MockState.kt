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
import kotlin.reflect.KClass

@PublishedApi
internal class MockState(private val owner: Any, spec: MockSpec, internal val descriptor: MockDescriptor) {
    private val representative: MockState? = spec.representative

    private val data: StateData = representative?.data ?: StateData(descriptor.memberCount)

    private val label: String = representative?.label ?: "${spec.name.orEmpty()}#${MockIds.next()}"

    internal val root: MockState
        get() = representative ?: this

    internal var group: ConstructorMocks? = null

    internal val name = "${descriptor.name}($label)"

    private val flags = spec.flags

    internal val isObject: Boolean
        get() = flags and OBJECT != 0

    internal val isStatic: Boolean
        get() = flags and STATIC != 0

    internal val isConstructor: Boolean
        get() = flags and CONSTRUCTOR != 0

    internal val isRegular: Boolean
        get() = representative != null || flags and SPY == 0

    internal val isSpy: Boolean
        get() = representative == null && flags and (SPY or OBJECT or STATIC) == SPY

    internal val forwardsCalls: Boolean
        get() = flags and DELEGATE != 0

    @Volatile
    private var capture: DefaultsCapture? = null

    internal val scope: LokkiScope? = if (representative != null) representative.scope else LokkiScope.owner()

    init {
        if (representative != null && !representative.descriptor.hasSameMembers(descriptor)) {
            throw LokkiException("the constructor mock of ${descriptor.name} has members of another shape")
        }
        if (descriptor.abi > LokkiAbi.VERSION) {
            throw LokkiException(
                "the mock of ${descriptor.name} was compiled by a newer lokki compiler plugin than lokki-core on " +
                    "the classpath: it needs ABI ${descriptor.abi}, lokki-core has ABI ${LokkiAbi.VERSION}; " +
                    "use the version of the lokki Gradle plugin for all lokki dependencies"
            )
        }
        if (descriptor.abi < OLDEST_ABI) {
            throw LokkiException(
                "the mock of ${descriptor.name} was compiled by an older lokki compiler plugin than lokki-core on " +
                    "the classpath supports: it has ABI ${descriptor.abi}, lokki-core needs ABI $OLDEST_ABI or newer; " +
                    "recompile it with the version of the lokki Gradle plugin for all lokki dependencies"
            )
        }
        if (representative == null) scope?.adopt(this)
    }

    fun answer(member: Int, arguments: Array<Any?>): Any? =
        respond(
            member,
            arguments,
            { record -> returned(record, mock.`lokki$original`(member, arguments)) },
        ) { answer, call, record -> returned(record, markingThrown(record) { answer.answer(call) }) }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> coAnswer(member: Int, arguments: Array<Any?>): T =
        respond(member, arguments, { mock.`lokki$coOriginal`(member, arguments) }) { answer, call, record ->
            if (record == null) answer.coAnswer(call) else coReturned(record, answer, call)
        } as T

    fun noOriginal(member: Int, arguments: Array<Any?>): Any? {
        if (flags and SPY != 0 && description(member).returnsUnit) return Unit
        throw LokkiException("no original implementation for ${call(member, arguments.joinToString { it.toStr() })}")
    }

    fun <T> stub(key: String, matchers: Array<Matcher<Any?>>): StubScope<T, T> {
        checkNotShared()
        return PendingStub(this, stubbedMember(key), matchers)
    }

    fun <T> stubLenient(key: String, matchers: Array<Matcher<Any?>>): StubScope<T, T> {
        checkNotShared()
        return PendingStub(this, stubbedMember(key), matchers, lenient = true)
    }

    private fun stubbedMember(key: String): Int {
        val member = descriptor.member(key)
        if (flags and DELEGATE != 0 && descriptor.isPrivate(member)) {
            throw LokkiException(
                "${memberName(member)} is private and $name delegates to another instance, whose calls of it " +
                    "the spy does not see; create the spy from a constructor call: spy(${descriptor.name}(...))"
            )
        }
        return member
    }

    fun link(key: String, matchers: Array<Matcher<Any?>>, stubs: Boolean, factory: (MockSpec) -> Any): Any {
        checkNotShared()
        return linkedChild(descriptor.member(key), matchers, stubs, factory)
    }

    fun forgetRecords() {
        data.forgetRecords()
    }

    fun exclude(key: String, matchers: Array<Matcher<Any?>>, current: Boolean = true) {
        val member = descriptor.member(key)
        if (member <= TO_STRING) return
        checkNotShared()
        data.exclude(Exclusion(member, matchers, nextMark()), current)
    }

    fun captureDefaults(key: String, names: Array<String?>, call: (Boolean) -> Unit): Array<Any?> =
        synchronized(data) {
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

    internal fun fieldAccessor(member: Int, access: FieldAccess): Int {
        val description = description(member)
        if (!description.hasDefaultField) {
            throw LokkiException(
                "${memberName(member)} has no backing field with default accessors: fieldValue is available for " +
                    "properties with default accessors, use callOriginal() for the others"
            )
        }
        return descriptor.accessor(member, access) ?: throw LokkiException(
            when {
                descriptor.accessor(member, FieldAccess.READ) == null ->
                    "${memberName(member)} is not a property accessor, so it has no fieldValue"
                else -> "${memberName(member)} belongs to a read-only property, so fieldValue cannot be written"
            }
        )
    }

    internal fun memberName(member: Int): String = "${descriptor.name}.${description(member).name}"

    internal fun description(member: Int): MethodDescription = descriptor.description(mock, member)

    internal fun register(member: Int, entry: StubEntry) {
        data.register(member, entry)
    }

    internal fun records(since: Long = ALL): List<CallRecord> = data.records(since)

    internal fun unansweredRecords(since: Long = ALL): List<CallRecord> = data.unansweredRecords(since)

    internal fun divergedRecords(): List<CallRecord> = data.divergedRecords()

    internal fun checkNotShared() {
        scope?.checkNotShared(name)
    }

    internal fun addWaiter(signal: RecordSignal) {
        data.addWaiter(signal)
    }

    internal fun removeWaiter(signal: RecordSignal) {
        data.removeWaiter(signal)
    }

    internal fun markVerified(calls: List<CallRecord>) {
        data.markVerified(calls)
    }

    internal fun confirmVerified(since: Long = ALL) {
        if (group != null) constructedStates().filter { it !== this }.forEach { it.confirmVerified(since) }
        val recorded = records(since)
        val unverified = data.unverified(recorded)
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
        val unused = describeStubs { !it.used && !it.lenient && it.mark > since }
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
        data.clear(answers, recordedCalls, childMocks, verificationMarks, exclusionRules, since)
    }

    internal fun chainPositions(): List<StubPosition> = data.chainPositions()

    internal fun call(member: Int, arguments: String): String = "$name.${description(member).name}($arguments)"

    private val mock: GeneratedMock
        get() = owner as GeneratedMock

    private inline fun respond(
        member: Int,
        arguments: Array<Any?>,
        original: (CallRecord?) -> Any?,
        answer: (Answer<Any?>, Call, CallRecord?) -> Any?,
    ): Any? {
        if (representative != null && data.isDetached) return original(null)
        val capture = capture
        if (capture != null && capture.member == member && capture.thread === currentThread()) {
            capture.arguments = arguments
            throw DefaultsCaptured()
        }
        val changesState = !Registration.isActive
        val link = if (changesState) null else Registration.takeLink(this, member)
        val record = when {
            changesState && member > TO_STRING && recordsCalls(member) -> data.record(root, member, arguments)
            else -> null
        }
        val stub = data.findStub(member, arguments) ?: return when {
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
            is ThrowingAnswer -> {
                if (selected.exception is Exception) markThrown(record)
                throw selected.exception
            }
            is ArgumentAnswer -> returned(record, arguments[selected.index])
            else -> answer(selected, Call(Invocation(owner, member, arguments), stub), record)
        }
    }

    private suspend fun coReturned(record: CallRecord, answer: Answer<Any?>, call: Call): Any? =
        returned(record, markingThrown(record) { answer.coAnswer(call) })

    private inline fun <T> markingThrown(record: CallRecord?, answer: () -> T): T =
        try {
            answer()
        } catch (e: Exception) {
            markThrown(record)
            throw e
        }

    private fun markThrown(record: CallRecord?) {
        if (record != null) data.markThrown(record)
    }

    private fun returned(record: CallRecord?, value: Any?): Any? {
        if (record != null && value is GeneratedMock) returnedMock(record, value.`lokki$state`())
        return value
    }

    private fun returnedMock(record: CallRecord, state: MockState) {
        record.result = state
        data.signal()
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
            val hint = when {
                !value.mockable || link != null -> ""
                value.file == null -> " or annotate the declaration that creates the mock with $CHAINED_MOCKS"
                else -> " or annotate the declaration in ${value.file} that creates the mock with $CHAINED_MOCKS"
            }
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
        if (record != null) data.markFailed(record, failure)
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
        synchronized(data) {
            if (data.stubs[member]?.lastOrNull()?.answersWith(child, matchers) != true) {
                register(member, StubEntry(matchers, listOf(ConstantAnswer(child)), nextMark()))
            }
        }
        return child
    }

    private fun child(member: Int, matchers: Array<Matcher<Any?>>, factory: (MockSpec) -> Any): Any =
        synchronized(data) {
            data.children.find { it.member == member && it.matchers.isEquivalentTo(matchers) }?.mock
                ?: factory(MockSpec(childName(), flags and (RELAXED or RELAX_UNIT_FUN)))
                    .also { data.children += Child(member, matchers, it, nextMark()) }
        }

    private fun childName(): String {
        val chained = CHILD_NAME.matchEntire(label) ?: return "child of $label"
        val depth = chained.groupValues[2].ifEmpty { "1" }.toInt()
        return "child^${depth + 1} of ${chained.groupValues[3]}"
    }

    private fun recordsCalls(member: Int): Boolean = flags and RECORD_PRIVATE_CALLS != 0 || !descriptor.isPrivate(member)

    private fun answersWithOriginal(member: Int): Boolean =
        flags and SPY != 0 && member != TO_STRING && (member > TO_STRING || flags and DELEGATE == 0)

    private fun relaxedAnswer(member: Int): Any? = mock.`lokki$relaxed`(member)

    private inline fun describeStubs(predicate: (StubEntry) -> Boolean): List<String> {
        val stubs = data.stubs
        return stubs.indices.flatMap { member ->
            stubs[member].orEmpty().filter(predicate).map { call(member, it.arguments) }
        }
    }

    internal fun fallBackTo(states: List<MockState>) {
        data.fallbacks = Array(states.size) { states[it].data }
    }

    internal fun detach() {
        data.isDetached = true
    }

    internal fun constructedStates(): List<MockState> = group?.statesOf(this) ?: listOf(this)
}

private class StateData(memberCount: Int) {
    @Volatile
    var stubs = arrayOfNulls<Array<StubEntry>>(memberCount)

    val records = ArrayList<CallRecord>()

    @Volatile
    var waiters = emptyArray<RecordSignal>()

    @Volatile
    var exclusions = emptyArray<Exclusion>()

    val children = ArrayList<Child>()

    @Volatile
    var isDetached = false

    @Volatile
    var fallbacks = emptyArray<StateData>()

    fun register(member: Int, entry: StubEntry) {
        synchronized(this) {
            val stubs = stubs.copyOf()
            stubs[member] = stubs[member]?.plus(entry) ?: arrayOf(entry)
            this.stubs = stubs
        }
    }

    fun findStub(member: Int, arguments: Array<Any?>): StubEntry? {
        ownStub(member, arguments)?.let { return it }
        for (fallback in fallbacks) fallback.ownStub(member, arguments)?.let { return it }
        return null
    }

    fun chainPositions(): List<StubPosition> =
        stubs.flatMap { entries -> entries.orEmpty().mapNotNull { it.chainPosition() } }

    fun record(state: MockState, member: Int, arguments: Array<Any?>): CallRecord? {
        val exclusions = exclusions
        if (exclusions.isNotEmpty() && exclusions.any { it.matches(member, arguments) }) return null
        val callStack = captureCallStack()
        return synchronized(records) {
            val record = CallRecord(state, member, arguments, nextSequenceNumber(), callStack)
            records += record
            signal()
            record
        }
    }

    fun signal() {
        for (waiter in waiters) waiter.signal()
    }

    fun records(since: Long): List<CallRecord> =
        synchronized(records) { records.filter { it.sequenceNumber > since } }

    fun unansweredRecords(since: Long): List<CallRecord> =
        synchronized(records) { records.filter { it.unanswered && it.sequenceNumber > since } }

    fun divergedRecords(): List<CallRecord> =
        synchronized(records) { records.filter { it.unanswered || it.threw } }

    fun unverified(recorded: List<CallRecord>): List<CallRecord> =
        synchronized(records) { recorded.filterNot { it.verified } }

    fun forgetRecords() {
        synchronized(records) { records.clear() }
    }

    fun markVerified(calls: List<CallRecord>) {
        synchronized(records) { calls.forEach { it.verified = true } }
    }

    fun markThrown(record: CallRecord) {
        synchronized(records) { record.threw = true }
    }

    fun markFailed(record: CallRecord, failure: LokkiException) {
        synchronized(records) { record.failure = failure }
    }

    fun addWaiter(signal: RecordSignal) {
        synchronized(records) { waiters += signal }
    }

    fun removeWaiter(signal: RecordSignal) {
        synchronized(records) { waiters = waiters.filter { it !== signal }.toTypedArray() }
    }

    fun exclude(exclusion: Exclusion, current: Boolean) {
        synchronized(this) { exclusions += exclusion }
        if (current) synchronized(records) { records.removeAll { exclusion.matches(it.member, it.arguments) } }
    }

    fun clear(
        answers: Boolean,
        recordedCalls: Boolean,
        childMocks: Boolean,
        verificationMarks: Boolean,
        exclusionRules: Boolean,
        since: Long,
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

    private fun ownStub(member: Int, arguments: Array<Any?>): StubEntry? {
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

    var threw = false

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
    val lenient: Boolean = false,
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

private class PendingStub<T, B>(
    private val state: MockState,
    private val member: Int,
    private val matchers: Array<Matcher<Any?>>,
    private val lenient: Boolean = false,
) : StubScope<T, B>, AdditionalAnswerScope<T, B> {
    private var entry: StubEntry? = null

    override fun answers(answer: Answer<T>): AdditionalAnswerScope<T, B> = add(listOf(answer))

    override fun answers(answer: AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B> =
        add(listOf(Answer { call -> AnswerScope<T, B>(call).answer(call) }))

    override fun coAnswers(answer: suspend AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B> =
        add(listOf(SuspendingAnswer { call -> AnswerScope<T, B>(call).answer(call) }))

    override fun returns(returnValue: T): AdditionalAnswerScope<T, B> = add(listOf(ConstantAnswer(returnValue)))

    override fun returnsMany(values: List<T>): AdditionalAnswerScope<T, B> = add(values.map(::ConstantAnswer))

    override fun returnsMany(vararg values: T): AdditionalAnswerScope<T, B> = returnsMany(values.asList())

    override fun returnsArgument(n: Int): AdditionalAnswerScope<T, B> {
        if (n !in matchers.indices) {
            throw LokkiException(
                "returnsArgument($n) is out of range: " +
                    "the number of arguments of ${state.memberName(member)} is ${matchers.size}"
            )
        }
        return add(listOf(ArgumentAnswer(n)))
    }

    override fun throws(ex: Throwable): AdditionalAnswerScope<T, B> = add(listOf(ThrowingAnswer(ex)))

    override fun throwsMany(exList: List<Throwable>): AdditionalAnswerScope<T, B> = add(exList.map(::ThrowingAnswer))

    @Suppress("UNCHECKED_CAST")
    override fun <K : Any> propertyType(cls: KClass<K>): StubScope<T, K> = this as StubScope<T, K>

    @Suppress("UNCHECKED_CAST")
    override fun <K : Any> nullablePropertyType(cls: KClass<K>): StubScope<T, K?> = this as StubScope<T, K?>

    override fun andThenAnswer(answer: Answer<T>): AdditionalAnswerScope<T, B> = answers(answer)

    override fun andThenAnswer(answer: AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B> = answers(answer)

    override fun coAndThen(answer: suspend AnswerScope<T, B>.(Call) -> T): AdditionalAnswerScope<T, B> = coAnswers(answer)

    override fun andThen(returnValue: T): AdditionalAnswerScope<T, B> = returns(returnValue)

    override fun andThenMany(values: List<T>): AdditionalAnswerScope<T, B> = returnsMany(values)

    override fun andThenMany(vararg values: T): AdditionalAnswerScope<T, B> = returnsMany(values.asList())

    override fun andThenThrows(ex: Throwable): AdditionalAnswerScope<T, B> = throws(ex)

    override fun andThenThrowsMany(exList: List<Throwable>): AdditionalAnswerScope<T, B> = throwsMany(exList)

    private fun add(answers: List<Answer<Any?>>): AdditionalAnswerScope<T, B> {
        val entry = entry
        when {
            entry != null -> entry.add(answers)
            answers.isEmpty() -> throw LokkiException("a stub needs at least one answer")
            else -> this.entry = StubEntry(matchers, answers, nextMark(), lenient).also { state.register(member, it) }
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

internal const val OLDEST_ABI = 7

private const val EQUALS = 0
private const val HASH_CODE = 1
private const val TO_STRING = 2
private const val LINK_HINT = "The call is a link of a call chain in a stubbed or verified call"
private const val CHAINED_MOCKS = "@ChainedMocks"
private val CHILD_NAME = Regex("child(\\^(\\d+))? of (.+)")
