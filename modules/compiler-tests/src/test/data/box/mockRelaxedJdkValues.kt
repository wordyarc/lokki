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
import java.util.stream.Stream

interface Account {
    fun balance(): BigDecimal
    fun owner(): Owner
    fun tags(): Stream<String>
    fun title(): CharSequence
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
import java.util.UUID
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
}

private fun missing(block: () -> Any?): String =
    try {
        "returned ${block()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val values = mock<Values>(relaxed = true)
    if (values.instant !== Instant.EPOCH) return "Fail instant: ${values.instant}"
    if (values.date() !== LocalDate.EPOCH) return "Fail date: ${values.date()}"
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

    val dateTime = missing { values.dateTime() }
    if (dateTime != "relaxed mock has no default for LocalDateTime returned by Values.dateTime(); stub the call") {
        return "Fail type without a zero: $dateTime"
    }
    val uuid = missing { values.uuid() }
    if (uuid != "relaxed mock has no default for UUID returned by Values.uuid(); stub the call") return "Fail: $uuid"

    val java = mock<JavaValues>(relaxed = true)
    if (java.created() !== Instant.EPOCH || java.price() !== BigDecimal.ZERO) return "Fail platform types"
    if (java.names().count() != 0L) return "Fail platform stream"

    val account = relaxedAccount()
    if (account.balance() !== BigDecimal.ZERO) return "Fail: the zero gave way to a child mock"
    if (account.owner().debt() !== BigDecimal.ZERO) return "Fail: the zero of a child mock"
    if (isMock(account.tags()) || account.title() != "") return "Fail: an empty value gave way to a child mock"
    return "OK"
}
