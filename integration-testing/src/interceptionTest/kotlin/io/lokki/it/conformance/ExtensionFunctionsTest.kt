package io.lokki.it.conformance

import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import io.lokki.mockObject
import io.lokki.mockStatic
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
class ExtensionFunctionsTest {
    @Test
    fun staticExtensionFunction() {
        mockStatic(IntWrapper::f)

        every {
            IntWrapper(5).f()
        } returns 11

        assertEquals(11, IntWrapper(5).f())
        assertEquals(25, IntWrapper(20).f())

        verify {
            IntWrapper(5).f()
            IntWrapper(20).f()
        }
    }

    @Test
    fun objectExtensionFunction() {
        mockObject(ExtObj)

        with(ExtObj) {
            every {
                IntWrapper(5).h()
            } returns 11

            assertEquals(11, IntWrapper(5).h())

            verify {
                IntWrapper(5).h()
            }
        }
    }

    @Test
    fun classExtensionFunction() {
        with(mock<ExtCls>()) {
            every {
                IntWrapper(5).g()
            } returns 11

            assertEquals(11, IntWrapper(5).g())

            verify {
                IntWrapper(5).g()
            }
        }
    }
}

open class ExtCls {
    open fun IntWrapper.g() = data + 5
}
