package io.lokki.it.conformance

import io.lokki.Awaits
import io.lokki.andThenJust
import io.lokki.coEvery
import io.lokki.coJustAwait
import io.lokki.coJustRun
import io.lokki.coVerify
import io.lokki.coVerifySequence
import io.lokki.confirmVerified
import io.lokki.mock
import io.lokki.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoJustAwaitTest {
    private val mock = mock<MockCls>()

    @Test
    fun `coJustRun completes as expected`() =
        runTest {
            coJustRun { mock.coOp() }

            val job = launch { mock.coOp() }
            runCurrent()

            coVerify(exactly = 1) { mock.coOp() }
            verify(exactly = 0) { mock.notImplemented() }
            confirmVerified(mock)
            assertTrue(job.isCompleted)
        }

    @Test
    fun `coJustAwait awaits until cancellation`() =
        runTest {
            coJustAwait { mock.coOp() }

            val job = launch { mock.coOp() }
            runCurrent()

            coVerify(exactly = 1) { mock.coOp() }
            verify(exactly = 0) { mock.notImplemented() }
            confirmVerified(mock)
            assertTrue(job.isActive)
            job.cancelAndJoin()
        }

    @Test
    fun `coJustAwait andThenJust answers and awaits until cancellation`() =
        runTest {
            coEvery { mock.coOp(any()) } answers { 1 } andThenJust Awaits

            val job =
                launch {
                    repeat(2) { mock.coOp(it) }
                }
            runCurrent()

            coVerifySequence {
                mock.coOp(0)
                mock.coOp(1)
            }
            verify(exactly = 0) { mock.notImplemented() }
            confirmVerified(mock)
            assertTrue(job.isActive)
            job.cancelAndJoin()
        }

    open class MockCls {
        @Suppress("RedundantSuspendModifier")
        open suspend fun coOp(a: Int = 1): Int = a + notImplemented()

        open fun notImplemented(): Int = TODO()
    }
}
