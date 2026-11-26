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
import java.util.ArrayDeque
import java.util.Deque
import java.util.LinkedList
import java.util.Queue
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.Callable
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    interface Backlog {
        val pending: Queue<String>
        val recent: Deque<String>
        val ordered: SortedSet<String>
        val byName: SortedMap<String, Int>
        val archive: LinkedList<String>
        val buffer: kotlin.collections.ArrayDeque<String>
        fun cursor(): Iterator<String>
        fun editor(): MutableListIterator<String>
    }

    private val invoice = mock<Invoice>(relaxed = true)

    @Test
    fun amountsReturnZero() {
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
    fun collectionsOfJavaUtilReturnEmptyOnes() {
        val backlog = mock<Backlog>(relaxed = true)

        assertIs<ArrayDeque<String>>(backlog.pending)
        assertIs<ArrayDeque<String>>(backlog.recent)
        assertIs<TreeSet<String>>(backlog.ordered)
        assertIs<TreeMap<String, Int>>(backlog.byName)
        assertTrue(backlog.pending.isEmpty() && backlog.recent.isEmpty() && backlog.ordered.isEmpty())
        assertTrue(backlog.byName.isEmpty() && backlog.archive.isEmpty() && backlog.buffer.isEmpty())
        backlog.pending.add("task")
        assertTrue(backlog.pending.isEmpty())
    }

    @Test
    fun iteratorsReturnEmptyOnes() {
        val backlog = mock<Backlog>(relaxed = true)

        assertFalse(backlog.cursor().hasNext())
        assertFalse(backlog.editor().hasPrevious())
    }

    @Test
    fun pointsInTimeHaveNoDefault() {
        assertEquals(
            listOf(
                "relaxed mock has no default for Instant returned by $invoice.getIssued(); stub the call",
                "relaxed mock has no default for LocalDate returned by $invoice.getDue(); stub the call",
                "relaxed mock has no default for LocalDateTime returned by $invoice.getPrinted(); stub the call",
            ),
            listOf(
                assertFailsWith<LokkiException> { invoice.issued }.message,
                assertFailsWith<LokkiException> { invoice.due }.message,
                assertFailsWith<LokkiException> { invoice.printed }.message,
            ),
        )
    }
}
