@file:OptIn(ExperimentalTime::class)

package sample

import io.lokki.LokkiException
import io.lokki.mock
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface Defaults {
    fun optional(): Optional<String>
    fun optionalInt(): OptionalInt
    fun optionalLong(): OptionalLong
    fun optionalDouble(): OptionalDouble
    val duration: Duration
    fun uByte(): UByte
    fun uShort(): UShort
    fun uInt(): UInt
    fun uLong(): ULong
    fun instant(): Instant
    fun nullableDuration(): Duration?
    fun nullableUInt(): UInt?
}

fun box(): String {
    val defaults = mock<Defaults>(relaxed = true)
    if (defaults.optional() != Optional.empty<String>()) return "optional"
    if (defaults.optionalInt() != OptionalInt.empty()) return "optionalInt"
    if (defaults.optionalLong() != OptionalLong.empty()) return "optionalLong"
    if (defaults.optionalDouble() != OptionalDouble.empty()) return "optionalDouble"
    if (defaults.duration != Duration.ZERO) return "duration"
    if (defaults.uByte() != 0.toUByte()) return "uByte"
    if (defaults.uShort() != 0.toUShort()) return "uShort"
    if (defaults.uInt() != 0u) return "uInt"
    if (defaults.uLong() != 0uL) return "uLong"
    val instant = try {
        "returned ${defaults.instant()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }
    if (instant != "relaxed mock has no default for Instant returned by Defaults.instant(); stub the call") return instant
    if (defaults.nullableDuration() != null) return "nullableDuration"
    if (defaults.nullableUInt() != null) return "nullableUInt"
    return "OK"
}
