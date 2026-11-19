package io.lokki.it.junit5

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.it.Car
import io.lokki.it.Garage
import io.lokki.junit5.CheckUnnecessaryStub
import io.lokki.junit5.ConfirmVerification
import io.lokki.junit5.KeepMocks
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import io.lokki.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class LokkiExtensionFailureTest {
    @Test
    fun callWithoutAnswerFailsTest() {
        val failure = runSample(CallWithoutAnswer::class).testFailures.getValue("callWithoutAnswer()")

        assertIs<AssertionError>(failure)
        assertTrue(failure.message!!.startsWith("1 call to mocks had no answer:\n1) Car(#"), failure.message)
        assertIs<LokkiException>(failure.cause)
        assertTrue(failure.suppressed.isEmpty())
    }

    @Test
    fun callWithoutAnswerIsSuppressedByFailureOfTest() {
        val failure = runSample(FailingTestWithCallWithoutAnswer::class).testFailures.getValue("fails()")

        assertEquals("failure of the test", failure.message)
        assertTrue(failure.suppressed.single().message!!.startsWith("1 call to mocks had no answer:"))
    }

    @Test
    fun dynamicTestsAreCheckedSeparately() {
        val results = runSample(DynamicCallWithoutAnswer::class)

        val failure = results.testFailures.getValue("failure with call without answer")

        assertEquals(setOf("without answer", "failure with call without answer"), results.testFailures.keys)
        assertEquals(listOf("with answer"), results.succeededTests)
        assertEquals("failure of the dynamic test", failure.message)
        assertTrue(failure.suppressed.single().message!!.startsWith("1 call to mocks had no answer:"))
    }

    @Test
    fun confirmVerificationChecksEveryTest() {
        val results = runSample(ConfirmedVerification::class)

        assertEquals(listOf("verified()"), results.succeededTests)
        assertTrue(results.testFailures.getValue("unverified()").message!!.startsWith("Verification acknowledgment failed"))
    }

    @Test
    fun confirmVerificationByConfigurationParameter() {
        assertEquals(emptyMap(), runSample(UnverifiedCall::class).testFailures)

        val results = runSample(UnverifiedCall::class, "lokki.junit.extension.confirmVerification" to "true")

        assertTrue(results.testFailures.getValue("unverified()").message!!.startsWith("Verification acknowledgment failed"))
    }

    @Test
    fun checkUnnecessaryStubChecksTestsAndClass() {
        val results = runSample(UnnecessaryStubs::class)
        val testFailure = results.testFailures.getValue("unusedStub()").message!!
        val classFailure = results.containerFailures.single().message!!

        assertContains(testFailure, ".drive(eq(1))")
        assertFalse(".drive(eq(2))" in testFailure)
        assertContains(classFailure, ".drive(eq(2))")
        assertFalse(".drive(eq(1))" in classFailure)
    }

    @Test
    fun checkUnnecessaryStubByConfigurationParameter() {
        val results = runSample(UnusedStub::class, "lokki.junit.extension.checkUnnecessaryStub" to "true")

        assertTrue(results.testFailures.getValue("unusedStub()").message!!.startsWith("Unnecessary stubbings detected."))
    }

    @Test
    fun stubsOfTestAreRolledBackForNextTest() {
        assertEquals(setOf("usesStubOfPreviousTest()"), runSample(StubForNextTest::class).testFailures.keys)
    }

    @Test
    fun keepMocksOnTestKeepsItsStubs() {
        val results = runSample(StubKeptByAnnotation::class)

        assertEquals(emptyMap(), results.testFailures)
        assertEquals(2, results.succeededTests.size)
    }

    @Test
    fun keepMocksByConfigurationParameter() {
        val results = runSample(StubForNextTest::class, "lokki.junit.extension.keepMocks" to "true")

        assertEquals(emptyMap(), results.testFailures)
    }

    @Test
    fun callWithoutAnswerOfKeptTestFailsClass() {
        val results = runSample(KeptCallWithoutAnswer::class)

        assertEquals(emptyMap(), results.testFailures)
        assertTrue(results.containerFailures.single().message!!.startsWith("1 call to mocks had no answer:"))
    }

    @Test
    fun autodetectedExtensionOpensScopes() {
        val failure = runSample(WithoutExtension::class).testFailures.getValue("operationWithoutMocks()")

        assertEquals(
            "no active LokkiScope for confirmVerified(): use LokkiExtension or lokkiScope { }",
            failure.message,
        )
        assertEquals(
            emptyMap(),
            runSample(WithoutExtension::class, "junit.jupiter.extensions.autodetection.enabled" to "true").testFailures,
        )
    }

    @Test
    fun twoRegistrationsShareScopes() {
        val results = runSample(TwoRegistrations::class)
        val failure = results.testFailures.getValue("callWithoutAnswer()")

        assertTrue(failure.message!!.startsWith("1 call to mocks had no answer:"), failure.message)
        assertTrue(failure.suppressed.isEmpty())
        assertEquals(listOf("stubOfPreviousTestIsRolledBack()"), results.succeededTests)
        assertEquals(emptyList(), results.containerFailures)
    }

    @Test
    fun failedConstructorClosesScope() {
        val failure = runSample(FailingConstructor::class).containerFailures.single()

        assertEquals("failure of the constructor", failure.message)
        assertTrue(failure.suppressed.single().message!!.startsWith("1 call to mocks had no answer:"))
        assertEquals(emptyMap(), runSample(StubOfStaticMock::class).testFailures)
    }

    @Test
    fun concurrentTestsFailForTheirOwnCallsWithoutAnswer() {
        val failures = runSample(ConcurrentCallsWithoutAnswer::class, *CONCURRENT_TESTS).testFailures

        assertEquals(setOf("first()", "second()"), failures.keys)
        for (failure in failures.values) {
            assertTrue(failure.message!!.startsWith("1 call to mocks had no answer:"), failure.message)
        }
    }

    @Test
    fun concurrentTestsCannotStubMockOfClass() {
        val failures = runSample(ConcurrentTestsOfSharedMock::class, *CONCURRENT_TESTS).testFailures

        assertEquals(setOf("first()", "second()"), failures.keys)
        for (failure in failures.values) {
            assertIs<LokkiException>(failure)
            assertContains(
                failure.message!!,
                "is used by concurrently running tests; create it per test or serialize them with @ResourceLock",
            )
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class CallWithoutAnswer {
        @Test
        fun callWithoutAnswer() {
            runCatching { Garage(mock()).leave() }
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class FailingTestWithCallWithoutAnswer {
        @Test
        fun fails() {
            runCatching { Garage(mock()).leave() }
            fail("failure of the test")
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class DynamicCallWithoutAnswer {
        @TestFactory
        fun calls() = listOf(
            dynamicTest("without answer") { runCatching { Garage(mock()).leave() } },
            dynamicTest("with answer") { Garage(mock { every { drive(any()) } returns "driven" }).leave() },
            dynamicTest("failure with call without answer") {
                runCatching { Garage(mock()).leave() }
                fail("failure of the dynamic test")
            },
        )
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @ConfirmVerification
    class ConfirmedVerification {
        private val car = mock<Car> { every { drive(any()) } returns "driven" }

        @Test
        fun verified() {
            Garage(car).leave()
            verify { car.drive(1) }
        }

        @Test
        fun unverified() {
            Garage(car).leave()
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class UnverifiedCall {
        @Test
        fun unverified() {
            Garage(mock { every { drive(any()) } returns "driven" }).leave()
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @CheckUnnecessaryStub
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class UnnecessaryStubs {
        private val car = mock<Car>()

        @BeforeAll
        fun stubForClass() {
            every { car.drive(2) } returns "class"
        }

        @Test
        fun unusedStub() {
            every { car.drive(1) } returns "test"
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class UnusedStub {
        @Test
        fun unusedStub() {
            mock<Car> { every { drive(1) } returns "test" }
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    class StubForNextTest {
        @Test
        @Order(1)
        fun stubsClassMock() {
            every { classMock.plate } returns "test"
        }

        @Test
        @Order(2)
        fun usesStubOfPreviousTest() {
            assertEquals("test", classMock.plate)
        }

        private companion object {
            lateinit var classMock: Car

            @BeforeAll
            @JvmStatic
            fun createClassMock() {
                classMock = mock { every { plate } returns "class" }
            }
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    class StubKeptByAnnotation {
        @Test
        @Order(1)
        @KeepMocks
        fun stubsClassMock() {
            every { classMock.plate } returns "test"
        }

        @Test
        @Order(2)
        fun usesStubOfPreviousTest() {
            assertEquals("test", classMock.plate)
        }

        private companion object {
            lateinit var classMock: Car

            @BeforeAll
            @JvmStatic
            fun createClassMock() {
                classMock = mock { every { plate } returns "class" }
            }
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @KeepMocks
    class KeptCallWithoutAnswer {
        @Test
        fun callWithoutAnswer() {
            runCatching { Garage(mock()).leave() }
        }
    }

    @Sample
    class WithoutExtension {
        @Test
        fun operationWithoutMocks() {
            confirmVerified()
        }
    }

    @OptIn(InternalLokkiApi::class)
    class OtherLokkiExtension : LokkiExtension()

    @Sample
    @ExtendWith(LokkiExtension::class, OtherLokkiExtension::class)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    class TwoRegistrations {
        @Test
        @Order(1)
        fun callWithoutAnswer() {
            every { shared.plate } returns "test"
            runCatching { Garage(mock()).leave() }
        }

        @Test
        @Order(2)
        fun stubOfPreviousTestIsRolledBack() {
            assertEquals("static", shared.plate)
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FailingConstructor {
        init {
            runCatching { Garage(mock()).leave() }
            fail("failure of the constructor")
        }

        @Test
        fun test() {
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class StubOfStaticMock {
        @Test
        fun stubsStaticMock() {
            every { shared.plate } returns "test"
            assertEquals("test", shared.plate)
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    class ConcurrentCallsWithoutAnswer {
        @Test
        fun first() {
            callAlongside()
        }

        @Test
        fun second() {
            callAlongside()
        }

        private fun callAlongside() {
            started.await(10, TimeUnit.SECONDS)
            runCatching { Garage(mock()).leave() }
            called.await(10, TimeUnit.SECONDS)
        }

        private companion object {
            val started = CyclicBarrier(2)
            val called = CyclicBarrier(2)
        }
    }

    @Sample
    @ExtendWith(LokkiExtension::class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.CONCURRENT)
    class ConcurrentTestsOfSharedMock {
        private val car = mock<Car>()

        @Test
        fun first() {
            stubAlongside("first")
        }

        @Test
        fun second() {
            stubAlongside("second")
        }

        private fun stubAlongside(answer: String) {
            started.await(10, TimeUnit.SECONDS)
            val stubbing = runCatching { every { car.drive(any()) } returns answer }
            stubbed.await(10, TimeUnit.SECONDS)
            stubbing.getOrThrow()
        }

        private companion object {
            val started = CyclicBarrier(2)
            val stubbed = CyclicBarrier(2)
        }
    }

    private companion object {
        val shared = mock<Car> { every { plate } returns "static" }

        val CONCURRENT_TESTS = arrayOf(
            "junit.jupiter.execution.parallel.enabled" to "true",
            "junit.jupiter.execution.parallel.mode.default" to "concurrent",
            "junit.jupiter.execution.parallel.config.strategy" to "fixed",
            "junit.jupiter.execution.parallel.config.fixed.parallelism" to "2",
        )
    }
}
