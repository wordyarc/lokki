package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class SealedClassMockingTest {
    @Test
    fun testSealedClassCanBeMocked() {
        val base =
            mock<Base> {
                every { i } returns 0
            }

        assertEquals(0, base.i)
    }

    private sealed class Base {
        abstract val i: Int
    }

    private open class Derived(
        override val i: Int,
    ) : Base()
}
