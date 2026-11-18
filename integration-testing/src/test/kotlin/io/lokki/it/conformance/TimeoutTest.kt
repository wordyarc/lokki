package io.lokki.it.conformance

import io.lokki.Called
import io.lokki.coVerify
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class TimeoutTest {
    interface MockCls {
        fun run()
    }

    val mock1 = mock<MockCls>(relaxed = true)
    val mock2 = mock<MockCls>(relaxed = true)

    @Test
    fun timeoutOnNeverCalled() {
        assertFails {
            verify(timeout = 500) {
                mock1.run()
            }
        }
    }

    @Test
    fun timeoutOnCalledLater() {
        launch {
            delay(1000)
            mock1.run()
        }

        assertFails {
            verify(timeout = 500) {
                mock1.run()
            }
        }
    }

    @Test
    fun okIfCalledEarlier() {
        launch {
            delay(200)
            mock1.run()
        }

        verify(timeout = 500) {
            mock1.run()
        }
    }

    @Test
    fun failIfOneMockCalled() {
        launch {
            delay(200)
            mock1.run()
        }

        assertFails {
            verify(timeout = 500) {
                mock1.run()
                mock2.run()
            }
        }
    }

    @Test
    fun failIfOneMockCalledAndOneLater() {
        launch {
            delay(200)
            mock1.run()
            delay(1000)
            mock2.run()
        }

        assertFails {
            verify(timeout = 500) {
                mock1.run()
                mock2.run()
            }
        }
    }

    @Test
    fun okIfBothInTime() {
        launch {
            delay(100)
            mock1.run()
            delay(100)
            mock2.run()
        }

        verify(timeout = 500) {
            mock1.run()
            mock2.run()
        }
    }

    @Test
    fun longTimeout() {
        launch {
            delay(500)
            mock1.run()
        }

        val start = System.currentTimeMillis()
        verify(timeout = 5000) {
            mock1.run()
        }
        val end = System.currentTimeMillis()
        val duration = end - start

        assertTrue(duration < 1500, "waiting too long")
    }

    @Test
    fun wasNotCalledFailsIfCalledBeforeTimeout() {
        launch {
            delay(200)
            mock1.run()
        }

        assertFails {
            verify(timeout = 500) {
                mock1 wasNot Called
            }
        }
    }

    @Test
    fun wasNotCalledOkIfCalledAfterTimeout() {
        launch {
            delay(1000)
            mock1.run()
        }

        verify(timeout = 500) {
            mock1 wasNot Called
        }
    }

    @Test
    fun exactlyZeroFailsIfCalledBeforeTimeout() {
        launch {
            delay(200)
            mock1.run()
        }

        assertFails {
            verify(exactly = 0, timeout = 500) {
                mock1.run()
            }
        }
    }

    @Test
    fun exactlyZeroOkIfCalledAfterTimeout() {
        launch {
            delay(1000)
            mock1.run()
        }

        verify(exactly = 0, timeout = 500) {
            mock1.run()
        }
    }

    @Test
    fun inverseFailsIfCalledBeforeTimeout() {
        launch {
            delay(200)
            mock1.run()
        }

        assertFails {
            verify(inverse = true, timeout = 500) {
                mock1.run()
            }
        }
    }

    @Test
    fun coVerifyExactlyOnceFailsIfCalledAgainBeforeTimeout() {
        launch {
            delay(100)
            mock1.run()
            delay(200)
            mock1.run()
        }

        assertFails {
            coVerify(exactly = 1, timeout = 2000) {
                mock1.run()
            }
        }
    }

    private fun launch(block: () -> Unit) {
        Thread(block).start()
    }

    private fun delay(ms: Long) = Thread.sleep(ms)
}
