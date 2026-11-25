package io.lokki.internal

import io.lokki.LokkiException
import kotlin.concurrent.Volatile
import kotlin.reflect.KType

@PublishedApi
internal object ReifiedMocks {
    private val registered = HashMap<KType, (MockSpec) -> Any>()

    @Volatile
    private var calls = emptyArray<ReifiedCall>()

    fun enter(types: Array<KType>, factories: Array<(MockSpec) -> Any>): ReifiedCall {
        val call = ReifiedCall(currentThread(), types, factories)
        synchronized(this) {
            types.forEachIndexed { index, type -> registered[type] = factories[index] }
            calls += call
        }
        return call
    }

    fun exit(call: ReifiedCall) {
        synchronized(this) { calls = calls.filter { it !== call }.toTypedArray() }
    }

    fun register(type: KType, factory: (MockSpec) -> Any) {
        synchronized(this) { registered[type] = factory }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(type: KType, spec: MockSpec): T {
        val factory = current(type) ?: synchronized(this) { registered[type] } ?: throw LokkiException(
            "mock<$type>() in an inline helper has no generated class: " +
                "apply the lokki plugin to the module that calls the helper"
        )
        return factory(spec) as T
    }

    private fun current(type: KType): ((MockSpec) -> Any)? {
        val calls = calls
        if (calls.isEmpty()) return null
        val thread = currentThread()
        for (index in calls.lastIndex downTo 0) {
            val call = calls[index]
            if (call.thread === thread) call.factory(type)?.let { return it }
        }
        return null
    }
}

@PublishedApi
internal class ReifiedCall internal constructor(
    internal val thread: Any,
    private val types: Array<KType>,
    private val factories: Array<(MockSpec) -> Any>,
) {
    internal fun factory(type: KType): ((MockSpec) -> Any)? =
        types.indexOf(type).takeIf { it >= 0 }?.let { factories[it] }
}
