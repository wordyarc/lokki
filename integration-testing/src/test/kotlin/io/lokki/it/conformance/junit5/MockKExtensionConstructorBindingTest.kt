package io.lokki.it.conformance.junit5

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.it.conformance.junit5.MockKExtensionConstructorBindingTest.Status.BAD
import io.lokki.it.conformance.junit5.MockKExtensionConstructorBindingTest.Status.CRITICAL
import io.lokki.it.conformance.junit5.MockKExtensionConstructorBindingTest.Status.GOOD
import io.lokki.junit5.LokkiExtension
import io.lokki.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
class MockKExtensionConstructorBindingTest(
    @Mock private val leftWing: LeftWing,
    @Spy private val rightWing: RightWing,
    @RelaxedMock private val enginePart: EnginePart,
    @Spy private val engine: Engine,
    @InjectMocks private val plane: Plane,
) {
    enum class Status {
        GOOD,
        BAD,
        CRITICAL,
    }

    open class LeftWing {
        open fun status() = GOOD

        open fun describe() = "Left wing"
    }

    open class RightWing {
        open fun status() = GOOD

        open fun describe() = "Right wing"
    }

    open class Engine(
        private val enginePart: EnginePart,
    ) {
        open fun status() = enginePart.status()

        open fun describe() = "Engine" + (enginePart.model() ?: "")
    }

    open class EnginePart {
        open fun status() = GOOD

        open fun model(): String? = null
    }

    class Plane(
        private val leftWing: LeftWing,
        private val rightWing: RightWing,
        private val engine: Engine,
    ) {
        fun describeComponents() = listOf(leftWing.describe(), rightWing.describe(), engine.describe()).joinToString(" ")

        fun state() = listOf(leftWing.status(), rightWing.status(), engine.status()).maxByOrNull { it.ordinal } ?: CRITICAL
    }

    @Test
    fun `injects mocks by constructor`() {
        every { leftWing.describe() } returns "LW"
        every { leftWing.status() } returns GOOD

        every { rightWing.describe() } returns "RW"

        every { enginePart.status() } returns BAD

        assertEquals(plane.state(), BAD)
        assertEquals(plane.describeComponents(), "LW RW Engine")
        verify { engine.describe() }
    }
}
