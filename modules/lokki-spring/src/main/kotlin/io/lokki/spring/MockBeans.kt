package io.lokki.spring

import io.lokki.clearMocks
import org.springframework.beans.factory.config.SingletonBeanRegistry

internal class MockBeans {
    private val mocks = ArrayList<Pair<Any, MockClear>>()

    fun add(mock: Any, clear: MockClear) {
        synchronized(mocks) { mocks += mock to clear }
    }

    fun clear(clear: MockClear) {
        val cleared = synchronized(mocks) { mocks.filter { it.second == clear }.map { it.first } }
        if (cleared.isNotEmpty()) clearMocks(cleared.first(), *cleared.drop(1).toTypedArray())
    }

    companion object {
        private val NAME = MockBeans::class.java.name

        fun of(registry: SingletonBeanRegistry): MockBeans =
            synchronized(registry) {
                registry.getSingleton(NAME) as MockBeans? ?: MockBeans().also { registry.registerSingleton(NAME, it) }
            }
    }
}
