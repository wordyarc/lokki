package io.lokki

import io.lokki.internal.ALL
import io.lokki.internal.CallRecord
import io.lokki.internal.CommonThreadLocal
import io.lokki.internal.MockState
import io.lokki.internal.Redirects
import io.lokki.internal.StaticInitializer
import io.lokki.internal.StubPosition
import io.lokki.internal.WeakReference
import io.lokki.internal.callSites
import io.lokki.internal.nextSequenceNumber
import io.lokki.internal.numbered
import io.lokki.internal.synchronized
import kotlin.concurrent.Volatile

internal class LokkiScope private constructor(private val parent: LokkiScope?, private val name: String) {
    val start: Long = if (parent == null) 0 else nextSequenceNumber()

    private val mocks = ArrayList<WeakReference<MockState>>()

    private val retained = ArrayList<MockState>()

    private val children = ArrayList<LokkiScope>()

    private val positions: List<StubPosition> = parent?.windowStates()?.flatMap { it.chainPositions() }.orEmpty()

    @Volatile
    var isClosed: Boolean = false
        private set

    fun close(failure: Throwable? = null) {
        val parent = synchronized(tree) {
            if (isClosed) return
            val parent = parent ?: throw LokkiException("$this cannot be closed")
            if (children.isNotEmpty()) {
                throw LokkiException("$this cannot be closed before its nested scopes: ${children.joinToString()}")
            }
            isClosed = true
            parent.children -= this
            parent
        }
        Redirects.release(this)
        val own = ownStates()
        val inherited = parent.windowStates()
        val unanswered = (inherited + own).flatMap { it.unansweredRecords(start) }.sortedBy { it.sequenceNumber }
        for (state in own) {
            state.clear(
                answers = true,
                recordedCalls = true,
                childMocks = true,
                verificationMarks = true,
                exclusionRules = true,
                since = ALL,
            )
        }
        clearWindow(inherited, answers = true, recordedCalls = true, childMocks = true)
        synchronized(mocks) { retained.clear() }
        if (unanswered.isEmpty()) return
        val error = AssertionError(unanswered.noAnswerReport(), unanswered.first().failure)
        if (failure == null) throw error
        failure.addSuppressed(error)
    }

    override fun toString(): String = name

    fun adopt(state: MockState) {
        synchronized(mocks) {
            mocks += WeakReference(state)
            if (parent != null && !isClosed) retained += state
        }
    }

    fun isWithin(ancestor: LokkiScope): Boolean = generateSequence(this) { it.parent }.any { it === ancestor }

    fun windowStates(): List<MockState> =
        generateSequence(this) { it.parent }.toList().asReversed().flatMap { it.ownStates() }

    fun clearWindow(states: List<MockState>, answers: Boolean, recordedCalls: Boolean, childMocks: Boolean) {
        for (state in states) {
            state.clear(
                answers,
                recordedCalls,
                childMocks,
                verificationMarks = true,
                exclusionRules = true,
                since = start,
            )
        }
        if (answers) positions.forEach { it.restore() }
    }

    fun checkNotShared(mock: String) {
        synchronized(tree) {
            var scope = this
            while (true) {
                if (scope.children.size > 1) {
                    throw LokkiException(
                        "mock $mock of $this is used by concurrently running tests; " +
                            "create it per test or serialize them with @ResourceLock"
                    )
                }
                scope = scope.children.singleOrNull() ?: return
            }
        }
    }

    private fun ownStates(): List<MockState> =
        synchronized(mocks) {
            mocks.removeAll { it.get() == null }
            mocks.mapNotNull { it.get() }
        }

    companion object {
        private val tree = Any()

        private val current = CommonThreadLocal<LokkiScope?>()

        val root: LokkiScope = LokkiScope(null, "the root scope")

        fun open(parent: LokkiScope, name: String): LokkiScope =
            synchronized(tree) {
                if (parent.isClosed) throw LokkiException("$name cannot be opened in $parent, which is closed")
                LokkiScope(parent, name).also { parent.children += it }
            }

        fun <R> withCurrent(scope: LokkiScope, block: () -> R): R {
            val previous = current.get()
            current.set(scope)
            try {
                return block()
            } finally {
                current.set(previous)
            }
        }

        val currentScope: LokkiScope?
            get() = current.get()

        fun owner(): LokkiScope? = if (StaticInitializer.isActive) root else current.get()

        fun requireCurrent(function: String): LokkiScope =
            current.get() ?: throw LokkiException(
                "no active LokkiScope for $function(): use LokkiExtension or lokkiScope { }"
            )
    }
}

public fun <R> lokkiScope(block: () -> R): R {
    val scope = LokkiScope.open(LokkiScope.currentScope ?: LokkiScope.root, "lokkiScope")
    val result = try {
        LokkiScope.withCurrent(scope, block)
    } catch (e: Throwable) {
        scope.close(e)
        throw e
    }
    scope.close()
    return result
}

private fun List<CallRecord>.noAnswerReport(): String {
    val calls = if (size == 1) "1 call to mocks" else "$size calls to mocks"
    return "$calls had no answer:\n${numbered()}\n${callSites()}"
}
