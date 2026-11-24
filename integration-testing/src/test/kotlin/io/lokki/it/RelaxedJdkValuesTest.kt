package io.lokki.it

import io.lokki.LokkiException
import io.lokki.mock
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class RelaxedJdkValuesTest {
    interface Invoice {
        val issued: Instant
        val due: LocalDate
        val grace: Duration
        val term: Period
        val total: BigDecimal
        val lines: BigInteger
        val paid: Instant?
        val printed: LocalDateTime
    }

    private val invoice = mock<Invoice>(relaxed = true)

    @Test
    fun typesThatNameTheirEpochOrZeroReturnIt() {
        assertSame(Instant.EPOCH, invoice.issued)
        assertSame(LocalDate.EPOCH, invoice.due)
        assertSame(Duration.ZERO, invoice.grace)
        assertSame(Period.ZERO, invoice.term)
        assertSame(BigDecimal.ZERO, invoice.total)
        assertSame(BigInteger.ZERO, invoice.lines)
    }

    @Test
    fun nullableTypeReturnsNull() {
        assertNull(invoice.paid)
    }

    @Test
    fun typeWithoutSuchConstantHasNoDefault() {
        assertEquals(
            "relaxed mock has no default for LocalDateTime returned by $invoice.getPrinted(); stub the call",
            assertFailsWith<LokkiException> { invoice.printed }.message,
        )
    }
}
