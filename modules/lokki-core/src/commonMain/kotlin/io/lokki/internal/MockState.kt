package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.StubScope
import kotlin.concurrent.Volatile

@PublishedApi
internal class MockState(private val descriptor: MockDescriptor) {
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
        val stub = findStub(member, arguments)
            ?: throw LokkiException("no answer found for ${descriptor.name}.${descriptor.memberName(member)}")
        return stub.value
    }

    fun <T> stub(key: String, matchers: Array<Matcher<Any?>>): StubScope<T> =
        PendingStub(this, descriptor.member(key), matchers)

    fun captureDefaults(key: String, call: () -> Unit): Array<Any?> = synchronized(this) {
        val capture = DefaultsCapture(descriptor.member(key), currentThread())
        this.capture = capture
        try {
            call()
        } catch (_: DefaultsCaptured) {
        } finally {
            this.capture = null
        }
        checkNotNull(capture.arguments) { "default arguments of $key were not captured" }
    }

    internal fun register(member: Int, entry: StubEntry) {
        synchronized(this) {
            val stubs = stubs.copyOf()
            stubs[member] = stubs[member]?.plus(entry) ?: arrayOf(entry)
            this.stubs = stubs
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

internal class StubEntry(private val matchers: Array<Matcher<Any?>>, val value: Any?) {
    fun matches(arguments: Array<Any?>): Boolean {
        for (index in matchers.indices) {
            if (!matchers[index].match(arguments[index])) return false
        }
        return true
    }
}

private class PendingStub<T>(
    private val state: MockState,
    private val member: Int,
    private val matchers: Array<Matcher<Any?>>,
) : StubScope<T> {
    override fun returns(returnValue: T) {
        state.register(member, StubEntry(matchers, returnValue))
    }
}

private class DefaultsCapture(val member: Int, val thread: Any) {
    var arguments: Array<Any?>? = null
}

private class DefaultsCaptured : RuntimeException()
