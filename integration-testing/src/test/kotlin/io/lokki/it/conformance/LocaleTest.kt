package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleTest {
    @Test
    fun shouldMockLocale() {
        val foo =
            mock<Foo> {
                every { getLocale() } returns Locale.GERMAN
            }

        assertEquals(Locale.GERMAN, foo.getLocale())
    }

    interface Foo {
        fun getLocale(): Locale
    }
}
