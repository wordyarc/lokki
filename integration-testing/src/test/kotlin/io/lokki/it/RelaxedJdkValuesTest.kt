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
import java.util.concurrent.Callable
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
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

    interface Feed {
        val title: CharSequence
        fun lines(): Stream<String>
        fun sizes(): IntStream
        fun offsets(): LongStream
        fun weights(): DoubleStream
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
    fun charSequenceAndStreamsReturnEmptyValues() {
        val feed = mock<Feed>(relaxed = true)

        assertEquals<CharSequence>("", feed.title)
        assertEquals(0L, feed.lines().count())
        assertEquals(0L, feed.sizes().count())
        assertEquals(0L, feed.offsets().count())
        assertEquals(0L, feed.weights().count())
    }

    @Test
    fun javaVoidReturnsNull() {
        assertNull(mock<Callable<Void>>(relaxed = true).call())
    }

    @Test
    fun typeWithoutSuchConstantHasNoDefault() {
        assertEquals(
            "relaxed mock has no default for LocalDateTime returned by $invoice.getPrinted(); stub the call",
            assertFailsWith<LokkiException> { invoice.printed }.message,
        )
    }
}
