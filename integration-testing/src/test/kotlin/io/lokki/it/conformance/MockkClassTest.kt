package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class MockkClassTest {
    val mock = mock<MockCls>()

    @Test
    fun exactlyZeroWithAny() {
        every { mock.op(3, 4) } returns 5

        assertEquals(5, mock.op(3, 4))

        verify { mock.op(3, 4) }
    }

    private val targetClass = mock<TestClass>()

    @Test
    fun testNothingIsNotThrowingNPE() {
        every { targetClass.alwaysThrows() } answers {
            throw IllegalArgumentException("this is a test")
        }
    }

    interface TestClass {
        fun alwaysThrows(): Nothing
    }

    interface MockCls {
        fun op(
            a: Int,
            b: Int,
        ): Int
    }
}
