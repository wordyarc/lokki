package io.lokki.it

import io.lokki.LokkiException
import io.lokki.checkUnnecessaryStub
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.verify
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScopeTest {
    @Test
    fun stubsOfScopeDoNotOutliveIt() {
        lokkiScope {
            every { car.plate } returns "test"
            every { car.drive(1) } returns "driven"
            assertEquals("test", Garage(car).plate())
            assertEquals("driven", Garage(car).leave())
        }

        assertEquals("static", Garage(car).plate())
        assertFailsWith<LokkiException> { Garage(car).leave() }
    }

    @Test
    fun callWithoutAnswerInCodeUnderTestFailsScope() {
        val error = assertFailsWith<AssertionError> {
            lokkiScope {
                val garage = Garage(mock())
                thread { runCatching { garage.leave() } }.join()
            }
        }

        assertTrue(error.message!!.startsWith("1 call to mocks had no answer:\n1) Car(#"), error.message)
        assertIs<LokkiException>(error.cause)
    }

    @Test
    fun operationsWithoutMocksCoverTheScope() {
        lokkiScope {
            val other = mock<Car>()
            every { other.drive(any()) } returns "driven"
            Garage(other).leave()

            assertFailsWith<AssertionError> { confirmVerified() }
            verify { other.drive(1) }
            confirmVerified()
            checkUnnecessaryStub()
        }
    }

    private companion object {
        val car = mock<Car> { every { plate } returns "static" }
    }
}
