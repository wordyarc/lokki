package io.lokki.internal

import kotlin.concurrent.Volatile

@PublishedApi
internal object Registration {
    private val registering = Threads()
    private val chains = Threads()

    @Volatile
    private var links = emptyArray<Link>()

    val isActive: Boolean
        get() = registering.containsCurrent()

    val isChain: Boolean
        get() = chains.containsCurrent()

    fun enter() {
        registering.add()
    }

    fun exit() {
        registering.remove()
    }

    fun enterChain() {
        chains.add()
    }

    fun exitChain() {
        chains.remove()
    }

    fun enterLink(receiver: Any?, key: String, stubs: Boolean, factory: ((MockSpec) -> Any)?, problem: String?): Link? {
        val state = (receiver as? GeneratedMock)?.`lokki$state` ?: return null
        val link = Link(currentThread(), state, state.descriptor.member(key), stubs, factory, problem)
        synchronized(this) { links += link }
        return link
    }

    fun exitLink(link: Link?) {
        if (link == null) return
        synchronized(this) { links = links.filter { it !== link }.toTypedArray() }
    }

    internal fun takeLink(state: MockState, member: Int): Link? {
        val links = links
        if (links.isEmpty()) return null
        val thread = currentThread()
        val link = links.lastOrNull { it.thread === thread && it.state === state && it.member == member }
        if (link == null || link.taken) return null
        link.taken = true
        return link
    }
}

@PublishedApi
internal class Link internal constructor(
    internal val thread: Any,
    internal val state: MockState,
    internal val member: Int,
    internal val stubs: Boolean,
    internal val factory: ((MockSpec) -> Any)?,
    internal val problem: String?,
) {
    internal var taken = false
}

private class Threads {
    @Volatile
    private var threads = emptyArray<Any>()

    fun containsCurrent(): Boolean {
        val threads = threads
        return threads.isNotEmpty() && currentThread() in threads
    }

    fun add() {
        synchronized(this) { threads += currentThread() }
    }

    fun remove() {
        synchronized(this) {
            val index = threads.lastIndexOf(currentThread())
            threads = (threads.take(index) + threads.drop(index + 1)).toTypedArray()
        }
    }
}
