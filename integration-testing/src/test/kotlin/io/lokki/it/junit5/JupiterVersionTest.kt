package io.lokki.it.junit5

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.it.Car
import io.lokki.it.Garage
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JupiterVersionTest {
    @Test
    fun extensionRequiresJupiter512() {
        val results = runSample(MockInTest::class)
        val (major, minor) = ExtendWith::class.java.`package`.implementationVersion.split('.').map(String::toInt)

        if (major > 5 || minor >= 12) {
            assertEquals(listOf("mockInTest()"), results.succeededTests)
        } else {
            val failure = results.containerFailures.single()
            assertIs<LokkiException>(failure)
            assertEquals("lokki-junit5 requires JUnit Jupiter 5.12+", failure.message)
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class MockInTest {
        @Test
        fun mockInTest() {
            Garage(mock<Car> { every { drive(any()) } returns "driven" }).leave()
        }
    }
}
