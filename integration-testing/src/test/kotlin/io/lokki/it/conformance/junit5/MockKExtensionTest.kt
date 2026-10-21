package io.lokki.it.conformance.junit5

import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(LokkiExtension::class)
class MockKExtensionTest {
    enum class Direction {
        NORTH,
        SOUTH,
        EAST,
        WEST,
    }

    enum class Outcome {
        FAILURE,
        RECORDED,
    }

    class RelaxedOutcome

    open class Car {
        open fun recordTelemetry(
            speed: Int,
            direction: Direction,
            lat: Double,
            long: Double,
        ): Outcome = Outcome.FAILURE

        open fun relaxedTest(): RelaxedOutcome? = null
    }

    @Mock
    private lateinit var car2: Car

    @RelaxedMock
    private lateinit var relaxedCar: Car

    @Spy
    private var carSpy = Car()

    @Test
    fun injectsValidMockInClass() {
        every {
            car2.recordTelemetry(
                speed = more(50),
                direction = Direction.NORTH,
                lat = any(),
                long = any(),
            )
        } returns Outcome.RECORDED

        val result = car2.recordTelemetry(51, Direction.NORTH, 1.0, 2.0)

        assertEquals(Outcome.RECORDED, result)
    }

    @Test
    fun injectsValidRelaxedMockInClass() {
        val result = relaxedCar.relaxedTest()

        assertNull(result)
    }

    @Test
    fun testInjectsValidSpyInClass() {
        val result = carSpy.relaxedTest()

        assertNull(result)

        verify { carSpy.relaxedTest() }
    }
}
