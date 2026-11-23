package io.lokki.it.junit5

import io.lokki.every
import io.lokki.junit5.CheckUnnecessaryStub
import io.lokki.junit5.ConfirmVerification
import io.lokki.junit5.KeepMocks
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

private interface Part {
    fun use(): Int
}

@Sample
@ExtendWith(LokkiExtension::class)
@ConfirmVerification
class ConfirmInNestedSample {
    @Nested
    inner class Inner {
        @Test
        fun unverifiedCall() {
            val part = mock<Part>()
            every { part.use() } returns 1
            part.use()
        }
    }
}

@Sample
@ExtendWith(LokkiExtension::class)
@CheckUnnecessaryStub
class UnnecessaryInNestedSample {
    @Nested
    inner class Inner {
        @Test
        fun unusedStub() {
            val part = mock<Part>()
            every { part.use() } returns 1
        }
    }
}

@Sample
@ExtendWith(LokkiExtension::class)
@ConfirmVerification
class StaticNestedSample {
    class Standalone {
        @Test
        fun ignoresOuterAnnotation() {
            val part = mock<Part>()
            every { part.use() } returns 1
            part.use()
        }
    }
}

@Sample
@ExtendWith(LokkiExtension::class)
@KeepMocks
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KeepInNestedSample {
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Inner {
        @Test
        @Order(1)
        fun stubs() {
            every { shared.use() } returns 5
        }

        @Test
        @Order(2)
        fun seesStubOfPreviousTest() {
            assertEquals(5, shared.use())
        }
    }

    private companion object {
        val shared = mock<Part>()
    }
}

class NestedAnnotationsTest {
    @Test
    fun confirmVerificationAppliesToNestedClass() {
        assertEquals(setOf("unverifiedCall()"), runSample(ConfirmInNestedSample::class).testFailures.keys)
    }

    @Test
    fun checkUnnecessaryStubAppliesToNestedClass() {
        assertEquals(setOf("unusedStub()"), runSample(UnnecessaryInNestedSample::class).testFailures.keys)
    }

    @Test
    fun staticNestedClassIsSeparate() {
        val results = runSample(StaticNestedSample.Standalone::class)
        assertEquals(emptySet(), results.testFailures.keys)
    }

    @Test
    fun keepMocksAppliesToNestedClass() {
        val results = runSample(KeepInNestedSample::class)
        assertEquals(emptySet(), results.testFailures.keys)
        assertEquals(2, results.succeededTests.size)
    }
}
