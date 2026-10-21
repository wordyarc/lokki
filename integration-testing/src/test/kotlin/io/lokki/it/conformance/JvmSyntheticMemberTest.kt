package io.lokki.it.conformance

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockObject
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmSyntheticMemberTest {
    interface SyntheticPropertyInterface {
        @get:JvmSynthetic
        val value: String

        @get:JvmSynthetic
        @set:JvmSynthetic
        var mutableValue: Int
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
    fun syntheticInterfacePropertySetterIsStubbed() {
        val mock = mock<SyntheticPropertyInterface>(relaxUnitFun = true)

        every { mock.mutableValue } returns 42

        assertEquals(42, mock.mutableValue)

        mock.mutableValue = 7

        verify { mock.mutableValue = 7 }
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

    @Test
    fun syntheticJvmStaticFunctionIsStubbed() {
        lokkiScope {
            mockObject(SyntheticStaticHolder) {
                every { SyntheticStaticHolder.compute("in") } returns "out"

                assertEquals("out", SyntheticStaticHolder.compute("in"))
                verify { SyntheticStaticHolder.compute("in") }
            }
        }
    }
}
