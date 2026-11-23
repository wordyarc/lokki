package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.it.mockable.Template
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InterfaceFinalizeTest {
    interface VoidFinalizeInterface {
        fun doSomething()

        fun finalize()
    }

    interface BooleanFinalizeInterface {
        fun doSomething()

        fun finalize(): Boolean
    }

    interface OneArgFinalizeInterface {
        fun doSomething()

        fun finalize(value: Int): Boolean
    }

    abstract class AbstractClassWithFinalize {
        abstract fun compute(): Int

        @Suppress("unused")
        protected fun finalize(): Unit = throw AssertionError("class-level finalize must never be invoked through the mock")
    }

    @Test
    fun interfaceVoidFinalizeIsStubbed() {
        val mock = mock<VoidFinalizeInterface>()
        every { mock.finalize() } just Runs
        mock.finalize()
        verify { mock.finalize() }
    }

    @Test
    fun interfaceBooleanFinalizeIsStubbed() {
        val mock = mock<BooleanFinalizeInterface>()
        every { mock.finalize() } returns true
        assertEquals(true, mock.finalize())
        verify { mock.finalize() }
    }

    @Test
    fun interfaceOneArgFinalizeIsStubbed() {
        val mock = mock<OneArgFinalizeInterface>()
        every { mock.finalize(5) } returns true
        assertEquals(true, mock.finalize(5))
        verify { mock.finalize(5) }
    }

    @Test
    fun relaxedInterfaceFinalizeIsAnswered() {
        val mock = mock<VoidFinalizeInterface>(relaxed = true)
        mock.finalize()
        verify { mock.finalize() }
    }

    @Test
    fun subclassProxyDoesNotOverrideClassLevelFinalize() {
        val mock = mock<AbstractClassWithFinalize>()
        every { mock.compute() } returns 42
        assertEquals(42, mock.compute())
        assertFailsWith<NoSuchMethodException> { mock.javaClass.getDeclaredMethod("finalize") }
    }

    @Test
    fun mockOfMockableClassDoesNotOverrideClassLevelFinalize() {
        val mock = mock<Template>(relaxed = true)
        assertFailsWith<NoSuchMethodException> { mock.javaClass.getDeclaredMethod("finalize") }
    }
}
