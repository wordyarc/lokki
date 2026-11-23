package io.lokki.internal

import io.lokki.LokkiException
import kotlin.reflect.KType

@PublishedApi
internal object ReifiedMocks {
    private val factories = HashMap<KType, (MockSpec) -> Any>()

    fun register(type: KType, factory: (MockSpec) -> Any) {
        synchronized(factories) { factories.getOrPut(type) { factory } }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(type: KType, spec: MockSpec): T {
        val factory = synchronized(factories) { factories[type] } ?: throw LokkiException(
            "mock<$type>() in an inline helper has no generated class: " +
                "apply the lokki plugin to the module that calls the helper"
        )
        return factory(spec) as T
    }
}
