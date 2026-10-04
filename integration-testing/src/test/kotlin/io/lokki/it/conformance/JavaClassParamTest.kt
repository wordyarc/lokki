package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertFalse

class JavaClassParamTest {
    interface MockCls {
        fun op(klass: Class<*>): Boolean

        fun op(
            first: Class<*>,
            second: Class<*>,
        ): Boolean
    }

    @Test
    fun matchingAnyClass() {
        val mock = mock<MockCls>()
        every { mock.op(any()) } returns false
        assertFalse(mock.op(Long::class.java))
        verify { mock.op(any()) }
    }

    @Test
    fun matchingAnyClassInSeveralParameters() {
        val mock = mock<MockCls>()
        every { mock.op(any(), any()) } returns false
        assertFalse(mock.op(String::class.java, Long::class.java))
        verify { mock.op(any(), any()) }
    }
}
