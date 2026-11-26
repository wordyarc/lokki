package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `relaxed interface property has no default instant`() {
        val mock = mock<InstantReturner>(relaxed = true)

        assertEquals(
            "relaxed mock has no default for Instant returned by $mock.getInstant(); stub the call",
            assertFailsWith<LokkiException> { mock.instant }.message,
        )
    }

    @Test
    fun `relaxed interface function has no default instant`() {
        assertFailsWith<LokkiException> { mock<InstantReturner>(relaxed = true).now() }
    }

    @Test
    fun `relaxed class property has no default instant`() {
        assertFailsWith<LokkiException> { mock<InstantHolder>(relaxed = true).instant }
    }

    @Test
    fun `stubbed instant survives real java-time formatting`() {
        val mock = mock<InstantReturner>(relaxed = true)
        every { mock.instant } returns Instant.fromEpochMilliseconds(0)

        assertEquals("1970-01-01T00:00:00Z", DateTimeFormatter.ISO_INSTANT.format(mock.instant.toJavaInstant()))
    }

    @Test
    fun `relaxed Duration property returns zero`() {
        assertEquals(Duration.ZERO, mock<DurationReturner>(relaxed = true).duration)
    }
}
