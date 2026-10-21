package io.lokki.it

import io.lokki.coVerify
import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpyTest {
    private val cars = listOf("A-1", "B-2", "C-3").map { plate -> mock<Car> { every { this@mock.plate } returns plate } }

    @Test
    fun spyRunsTheOriginalOfMainAndRecordsItsOwnCalls() {
        val workshop = spy(Workshop("north"))

        assertEquals(listOf("A-1 at north", "B-2 at north"), workshop.service(cars))
        verify(exactly = 2) { workshop.schedule(any()) }
        verify(exactly = 0) { workshop.open() }
        assertEquals(listOf("open"), workshop.log())
    }

    @Test
    fun stubbedInternalMemberAnswersCallsFromMain() {
        val workshop = spy(Workshop("south", capacity = 3))
        every { workshop.schedule(cars[1]) } returns "later"

        assertEquals(listOf("A-1 at south", "later", "C-3 at south"), workshop.service(cars))
    }

    @Test
    fun spyOfSuspendMemberRunsTheOriginal() = runTest {
        val workshop = spy<Workshop>(Workshop("east"))

        assertEquals("inspected A-1", workshop.inspect(cars[0]))
        coVerify { workshop.inspect(cars[0]) }
    }

    @Test
    fun spyOfInterfaceDelegatesToTheInstance() {
        val radio = mock<Radio>()
        val car = spy<Car>(
            object : Car {
                override val plate: String = "D-4"
                override val radio: Radio = radio

                override fun drive(distance: Int): String = "drove $distance"
            },
        )
        every { car.drive(1) } returns "stuck"

        assertEquals("stuck", Garage(car).leave())
        assertEquals("D-4", Garage(car).plate())
        verify { car.drive(1) }
    }
}
