package io.lokki.it.conformance

import io.lokki.every
import io.lokki.it.conformance.spycopy.Counter
import io.lokki.it.conformance.spycopy.Person
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertEquals

class SpykObjectToCopyPropertyTest {
    @Test
    fun defaultConstructorPathUsesMockedProperties() {
        val mockedPerson =
            spy<Person> {
                every { firstName } returns "John"
                every { surname } returns "Doe"
            }

        assertEquals("John", mockedPerson.firstName)
        assertEquals("John Doe", mockedPerson.fullname)
    }

    @Test
    fun objectToCopyPathUsesMockedProperties() {
        val mockedPerson =
            spy(Person()) {
                every { firstName } returns "John"
                every { surname } returns "Doe"
            }

        assertEquals("John", mockedPerson.firstName)
        assertEquals("John Doe", mockedPerson.fullname)
    }

    @Test
    fun objectToCopyPathLazyComputesFromCopiedState() {
        val mockedCounter = spy(Counter().apply { hits = 42 })

        assertEquals("hits=42", mockedCounter.report)
    }

    @Test
    fun objectToCopyPathLazyUsesMockedProperties() {
        val mockedCounter =
            spy(Counter().apply { hits = 42 }) {
                every { hits } returns 7
            }

        assertEquals(7, mockedCounter.hits)
        assertEquals("hits=7", mockedCounter.report)
    }
}
