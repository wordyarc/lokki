package io.lokki.it.conformance

import io.lokki.every
import io.lokki.it.conformance.privateparent.*
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateParentMethodTest {
    @Test
    fun testChildAlwaysMockedFirst() {
        val mock =
            mock<ChildWithShadowedMethod> {
                every { call() } answers { callOriginal() }
                every { this@mock["callPrivate"]() } returns "Mock"
            }

        assertEquals(mock.call(), "Mock")
    }

    @Test
    fun testPrivateCallMock() {
        val mock =
            mock<Child> {
                every { call() } answers { callOriginal() }
                every { this@mock["callPrivate"]() } returns "Mock"
            }

        assertEquals(mock.call(), "Mock")
    }

    @Test
    fun testPrivateCallMockForGrandChild() {
        val mock =
            mock<GrandChild> {
                every { call() } answers { callOriginal() }
                every { this@mock["callPrivate"]() } returns "Mock"
            }

        assertEquals(mock.call(), "Mock")
    }

    @Test
    fun testPrivateCallVerify() {
        val mock = spy(Child(), recordPrivateCalls = true)

        mock.call()

        verify { mock["callPrivate"]() }
    }
}
