package io.lokki.it.spring

import io.lokki.LokkiException
import io.lokki.isMock
import io.lokki.it.junit5.Sample
import io.lokki.it.junit5.containerFailures
import io.lokki.it.junit5.runSample
import io.lokki.it.junit5.succeededTests
import io.lokki.spring.MockBean
import org.springframework.core.SpringVersion
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringVersionTest {
    @Test
    fun lokkiSpringRequiresSpring626() {
        val results = runSample(MockBeanSample::class)
        val version = SpringVersion.getVersion()
        val (major, minor, patch) = version.split('.').map { part -> part.takeWhile(Char::isDigit).toInt() }

        if (KotlinVersion(major, minor, patch) >= KotlinVersion(6, 2, 6)) {
            assertEquals(listOf("usesMock()"), results.succeededTests)
        } else {
            val failure = results.containerFailures.single()
            val lokki = generateSequence(failure) { it.cause }.filterIsInstance<LokkiException>().first()
            assertEquals("lokki-spring requires Spring Framework 6.2.6 or newer, but the test runs on $version", lokki.message)
        }
    }

    @Sample
    @SpringJUnitConfig(Beans::class)
    class MockBeanSample {
        @MockBean(relaxed = true)
        lateinit var greeter: Greeter

        @Test
        fun usesMock() {
            assertTrue(isMock(greeter))
        }
    }
}
