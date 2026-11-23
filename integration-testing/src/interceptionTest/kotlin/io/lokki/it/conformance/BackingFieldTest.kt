package io.lokki.it.conformance

import io.lokki.every
import io.lokki.it.conformance.backingfield.*
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertEquals

class BackingFieldTest {
    val mock = spy(MockCls(), recordPrivateCalls = true)

    @Test
    fun mockGetIntProperty() {
        every { mock.intProp } answers { fieldValue + 6 }

        assertEquals(11, mock.intProp)
    }

    @Test
    fun mockSetIntProperty() {
        every { mock.intProp = any() } propertyType Int::class answers { fieldValue += value }

        mock.intProp = 3
        mock.intProp = 4

        assertEquals(12, mock.intProp)
    }

    @Test
    fun mockDynamicGetIntProperty() {
        every { mock getProperty "intProp" } propertyType Int::class answers { fieldValue + 6 }

        assertEquals(11, mock.intProp)
    }

    @Test
    fun mockDynamicSetIntProperty() {
        every {
            mock setProperty "intProp" value any<Int>()
        } propertyType Int::class answers { fieldValue += value }

        mock.intProp = 3
        mock.intProp = 4

        assertEquals(12, mock.intProp)
    }

    @Test
    fun mockSetManyIntProperty() {
        every {
            mock.intProp = any()
        } propertyType Int::class answers {
            fieldValue = value + 1
        } andThenAnswer {
            fieldValue = value - 1
        }

        mock.intProp = 3
        assertEquals(4, mock.intProp)
        mock.intProp = 4
        assertEquals(3, mock.intProp)
    }
}
