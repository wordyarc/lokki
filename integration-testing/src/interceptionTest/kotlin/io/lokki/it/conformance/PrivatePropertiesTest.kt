package io.lokki.it.conformance

import io.lokki.Runs
import io.lokki.every
import io.lokki.it.conformance.privateproperties.*
import io.lokki.just
import io.lokki.spy
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivatePropertiesTest {
    @Test
    fun testPrivateProperty() {
        val mock = spy(Team(), recordPrivateCalls = true)

        every { mock getProperty "person" } returns Person("Big Ben")
        every { mock setProperty "person" value Person("test") } just Runs
        every { mock invoke "fn" withArguments listOf(5) } returns 3

        assertEquals("Big Ben", mock.memberName)
        assertEquals(3, mock.pubFn(5))

        mock.memberName = "test"
        assertEquals("Big Ben", mock.memberName)

        verify { mock getProperty "person" }
        verify { mock setProperty "person" value Person("test") }
        verify { mock invoke "fn" withArguments listOf(5) }
    }
}
