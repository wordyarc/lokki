package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Issue1538FailingClinitMockTest {
    open class ClinitFailingClass {
        companion object {
            init {
                throw IllegalStateException("simulated failing <clinit>, see #1538")
            }
        }
    }

    @Test
    fun `mock of a class with failing clinit fails with the initialization failure`() {
        val failure = assertFailsWith<LokkiException> { mock<ClinitFailingClass>() }

        val chain = generateSequence<Throwable>(failure) { it.cause }.toList()
        assertTrue(chain.none { it is InternalError }, "expected the initialization failure to be reported, got: $failure")
        val initializationFailure = chain.filterIsInstance<LinkageError>().first()
        assertEquals("simulated failing <clinit>, see #1538", initializationFailure.cause?.message)
    }
}
