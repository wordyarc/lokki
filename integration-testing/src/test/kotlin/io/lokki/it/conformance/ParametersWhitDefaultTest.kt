package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class ParametersWhitDefaultTest {
    private val target = mock<DefaultParam>()

    @Test
    fun testAgainstDefaultParam() {
        every { target.foo() } returns STUB_STRING

        assertEquals(target.foo(), STUB_STRING)
    }

    private companion object {
        const val STUB_STRING = "A string"
    }

    interface DefaultParam {
        fun foo(param: String = "default"): String
    }
}
