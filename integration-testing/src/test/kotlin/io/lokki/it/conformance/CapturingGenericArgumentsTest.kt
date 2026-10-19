package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import kotlin.test.Test

class CapturingGenericArgumentsTest {
    @Test
    fun captureGenericArgument() {
        val mock = createMock<Int>()
        mock.tryBar(3)
    }

    interface Foo<in T> {
        fun bar(value: T)
    }

    private inline fun <reified T : Any> createMock(): Foo<Any> {
        val slot = slot<T>()

        return mock {
            every {
                bar(capture(slot))
            } just Runs
        }
    }

    private fun <T> Foo<T>.tryBar(value: T) {
        bar(value)
    }
}
