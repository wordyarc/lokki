// FILE: sample/JavaValues.java
package sample;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

public interface JavaValues {
    Instant created();

    BigDecimal price();

    Stream<String> names();
}

// FILE: children.kt
@file:ChainedMocks

package sample

import io.lokki.ChainedMocks
import io.lokki.mock
import java.math.BigDecimal
import java.util.Queue
import java.util.stream.Stream

interface Account {
    fun balance(): BigDecimal
    fun owner(): Owner
    fun tags(): Stream<String>
    fun title(): CharSequence
    fun pending(): Queue<String>
    fun history(): Iterator<String>
}

interface Owner {
    fun debt(): BigDecimal
}

fun relaxedAccount(): Account = mock<Account>(relaxed = true)

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.isMock
import io.lokki.mock
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.Deque
import java.util.LinkedList
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.PriorityQueue
import java.util.Queue
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream

interface Values {
    val instant: Instant
    fun date(): LocalDate
    fun duration(): Duration
    fun period(): Period
    fun decimal(): BigDecimal
    fun integer(): BigInteger
    fun nullableInstant(): Instant?
    fun nullableDecimal(): BigDecimal?
    fun stream(): Stream<String>
    fun ints(): IntStream
    fun longs(): LongStream
    fun doubles(): DoubleStream
    fun dateTime(): LocalDateTime
    fun uuid(): UUID
    fun queue(): Queue<String>
    fun deque(): Deque<String>
    fun sortedSet(): SortedSet<String>
    fun navigableSet(): NavigableSet<String>
    fun sortedMap(): SortedMap<String, Int>
    fun navigableMap(): NavigableMap<String, Int>
    fun linkedList(): LinkedList<String>
    fun treeSet(): TreeSet<String>
    fun treeMap(): TreeMap<String, Int>
    fun arrayDeque(): java.util.ArrayDeque<String>
    fun priorityQueue(): PriorityQueue<String>
    fun concurrentMap(): ConcurrentHashMap<String, Int>
}

private fun missing(block: () -> Any?): String =
    try {
        "returned ${block()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val values = mock<Values>(relaxed = true)
    if (values.duration() !== Duration.ZERO) return "Fail duration: ${values.duration()}"
    if (values.period() !== Period.ZERO) return "Fail period: ${values.period()}"
    if (values.decimal() !== BigDecimal.ZERO) return "Fail decimal: ${values.decimal()}"
    if (values.integer() !== BigInteger.ZERO) return "Fail integer: ${values.integer()}"
    if (values.nullableInstant() != null || values.nullableDecimal() != null) return "Fail nullable"
    val counts = listOf(
        values.stream().count(), values.ints().count(), values.longs().count(), values.doubles().count(),
    )
    if (counts.any { it != 0L }) return "Fail streams: $counts"
    if (values.stream() === values.stream()) return "Fail: a stream is shared"

    val collections = listOf<Any>(
        values.queue(), values.deque(), values.sortedSet(), values.navigableSet(), values.sortedMap(),
        values.navigableMap(), values.linkedList(), values.treeSet(), values.treeMap(), values.arrayDeque(),
        values.priorityQueue(),
    )
    val expectedClasses = listOf(
        "ArrayDeque", "ArrayDeque", "TreeSet", "TreeSet", "TreeMap", "TreeMap", "LinkedList", "TreeSet", "TreeMap",
        "ArrayDeque", "PriorityQueue",
    )
    if (collections.map { it.javaClass.simpleName } != expectedClasses) return "Fail collection classes: $collections"
    if (collections.any { if (it is Map<*, *>) it.isNotEmpty() else (it as Collection<*>).isNotEmpty() }) {
        return "Fail empty collections: $collections"
    }
    if (values.queue() === values.queue()) return "Fail: a queue is shared"

    val points = listOf(
        missing { values.instant }, missing { values.date() }, missing { values.dateTime() }, missing { values.uuid() },
    )
    val expectedPoints = listOf(
        "relaxed mock has no default for Instant returned by Values.getInstant(); stub the call",
        "relaxed mock has no default for LocalDate returned by Values.date(); stub the call",
        "relaxed mock has no default for LocalDateTime returned by Values.dateTime(); stub the call",
        "relaxed mock has no default for UUID returned by Values.uuid(); stub the call",
    )
    if (points != expectedPoints) return "Fail types without an empty value: $points"
    val concurrent = missing { values.concurrentMap() }
    if (!concurrent.startsWith("relaxed mock has no default for ConcurrentHashMap<String, Int> returned by")) {
        return "Fail concurrent collection: $concurrent"
    }

    val java = mock<JavaValues>(relaxed = true)
    if (java.price() !== BigDecimal.ZERO) return "Fail platform types"
    if (java.names().count() != 0L) return "Fail platform stream"
    val created = missing { java.created() }
    if (created != "relaxed mock has no default for Instant returned by JavaValues.created(); stub the call") {
        return "Fail platform type without an empty value: $created"
    }

    val account = relaxedAccount()
    if (account.balance() !== BigDecimal.ZERO) return "Fail: the zero gave way to a child mock"
    if (account.owner().debt() !== BigDecimal.ZERO) return "Fail: the zero of a child mock"
    if (isMock(account.tags()) || account.title() != "") return "Fail: an empty value gave way to a child mock"
    if (isMock(account.pending()) || isMock(account.history())) return "Fail: a child mock of a collection"
    return "OK"
}
