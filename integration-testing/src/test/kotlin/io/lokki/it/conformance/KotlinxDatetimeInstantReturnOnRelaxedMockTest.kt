package io.lokki.it.conformance

import io.lokki.mock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class KotlinxDatetimeInstantReturnOnRelaxedMockTest {
    interface InstantReturner {
        val instant: Instant

        fun now(): Instant
    }

    open class InstantHolder {
        open val instant: Instant get() = Instant.fromEpochMilliseconds(42)
    }

    interface DurationReturner {
        val duration: Duration
    }

    @Test
    fun `relaxed interface property returns a real epoch instant`() {
        assertRealEpochInstant(mock<InstantReturner>(relaxed = true).instant)
    }

    @Test
    fun `relaxed interface function returns a real epoch instant`() {
        assertRealEpochInstant(mock<InstantReturner>(relaxed = true).now())
    }

    @Test
    fun `relaxed class property returns a real epoch instant`() {
        assertRealEpochInstant(mock<InstantHolder>(relaxed = true).instant)
    }

    @Test
    fun `relaxed instant survives real java-time formatting`() {
        val mock = mock<InstantReturner>(relaxed = true)
        assertEquals("1970-01-01T00:00:00Z", DateTimeFormatter.ISO_INSTANT.format(mock.instant.toJavaInstant()))
    }

    @Test
    fun `relaxed Duration property returns zero`() {
        assertEquals(Duration.ZERO, mock<DurationReturner>(relaxed = true).duration)
    }

    private fun assertRealEpochInstant(instant: Instant) {
        assertEquals(Instant.fromEpochMilliseconds(0), instant)
        assertEquals("1970-01-01T00:00:00Z", instant.toString())
        assertEquals(java.time.Instant.EPOCH, instant.toJavaInstant())
    }
}
