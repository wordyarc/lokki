package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSyntheticMemberTest {
    interface SyntheticPropertyInterface {
        @get:JvmSynthetic
        val value: String
    }

    interface SyntheticFunctionInterface {
        @JvmSynthetic
        fun compute(arg: String): String
    }

    abstract class SyntheticPropertyClass {
        @get:JvmSynthetic
        abstract val value: String
    }

    @Test
    fun syntheticInterfacePropertyGetterIsStubbed() {
        val mock = mock<SyntheticPropertyInterface>()

        every { mock.value } returns "stubbed"

        assertEquals("stubbed", mock.value)
        verify { mock.value }
    }

    @Test
    fun syntheticInterfaceFunctionIsStubbed() {
        val mock = mock<SyntheticFunctionInterface>()

        every { mock.compute("in") } returns "out"

        assertEquals("out", mock.compute("in"))
        verify { mock.compute("in") }
    }

    @Test
    fun syntheticAbstractClassPropertyGetterIsStubbed() {
        val mock = mock<SyntheticPropertyClass>()

        every { mock.value } returns "stubbed"

        assertEquals("stubbed", mock.value)
    }
}
