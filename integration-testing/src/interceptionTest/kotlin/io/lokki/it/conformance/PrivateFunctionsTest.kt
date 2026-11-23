package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.it.conformance.privatefunctions.*
import io.lokki.junit5.LokkiExtension
import io.lokki.just
import io.lokki.justRun
import io.lokki.mockObject
import io.lokki.spy
import io.lokki.verify
import io.lokki.verifySequence
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.extension.ExtendWith

@Suppress("UNUSED_PARAMETER")
@ExtendWith(LokkiExtension::class)
class PrivateFunctionsTest {
    @Test
    fun mockPrivateMethodWithGeneric() {
        val mock = spy<GenericsCls>()

        every {
            mock["updateItemInDb"](any<Long>(), any<String>(), any()) as Unit
        } just Runs

        mock.pubCall()
    }

    @Test
    fun spyPrivateMethod() {
        val mock = spy<Abc>(recordPrivateCalls = true)
        every { mock["x"]() } returns "def"
        assertEquals("def", mock.y())
        verifySequence {
            mock.y()
            mock["x"]()
        }
    }

    @Test
    fun objectPrivateMethod() {
        mockObject(Def, recordPrivateCalls = true) {
            every { Def["x"]() } returns "ghi"
            assertEquals("ghi", Def.y())
            verify {
                Def.y()
                Def["x"]()
            }
        }
    }

    @Test
    fun spyNoRecordingPrivateMethod() {
        val mock = spy<Abc>()
        every { mock["x"]() } returns "def"
        assertEquals("def", mock.y())
        verifySequence {
            mock.y()
        }
    }

    @Test
    fun objectNoRecordingPrivateMethod() {
        mockObject(Def) {
            every { Def["x"]() } returns "ghi"
            assertEquals("ghi", Def.y())
            verifySequence {
                Def.y()
            }
        }
    }

    @Test
    fun privateCallsWithNullability() {
        val mock = spy<MockCls>(recordPrivateCalls = true)
        every { mock["x"](any<Int>(), any<Int>(), any<Def>()) } returns "test"

        assertEquals("test", mock.y(1, 2, null))

        verify { mock["x"](any<Int>(), any<Int>(), any<Def>()) }
    }

    @Test
    fun mockPrivateMethodThatReturnsNothing() {
        val myClass = spy(PrivateNoReturnCls(), recordPrivateCalls = true)
        every { myClass invokeNoArgs "myPrivateMethod" } returns Unit

        myClass.myPublicMethod()

        verify {
            myClass invokeNoArgs "myPrivateMethod"
        }
    }

    @Test
    fun justRunsWithPrivateMethod() {
        val mock = spy<PrivateNoReturnCls>(recordPrivateCalls = true)

        justRun { mock invokeNoArgs "myPrivateMethod" }

        mock.myPublicMethod()
    }
}
