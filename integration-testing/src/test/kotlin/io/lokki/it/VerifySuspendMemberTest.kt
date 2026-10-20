package io.lokki.it

import io.lokki.coEvery
import io.lokki.coJustRun
import io.lokki.coVerify
import io.lokki.confirmVerified
import io.lokki.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VerifySuspendMemberTest {
    private suspend fun destination(): String = "work"

    @Test
    fun suspendCallsThroughMainAreVerified() = runTest {
        val navigator = mock<Navigator>()
        coJustRun { navigator.reset() }
        coEvery { navigator.route(destination()) } returns listOf("home", "work")

        assertEquals("home -> work", Trip(navigator).plan("work"))

        coVerify {
            navigator.reset()
            navigator.route(destination())
        }
        coVerify(exactly = 0) { navigator.route(any(), avoidTolls = true) }
        confirmVerified(navigator)
    }
}
