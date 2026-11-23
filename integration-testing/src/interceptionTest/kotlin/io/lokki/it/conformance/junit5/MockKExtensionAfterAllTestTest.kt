package io.lokki.it.conformance.junit5

import io.lokki.isMock
import io.lokki.junit5.LokkiExtension
import io.lokki.mockObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class MockKExtensionAfterAllTestTest {
    @Test
    open fun prepareAfterAllUnmockTest() {
        mockObject(TestMock)
        assertTrue(isMock(TestMock, objectMock = true))
    }

    @ExtendWith(CheckIsNotMock::class, LokkiExtension::class)
    class AfterAllClearMocks : MockKExtensionAfterAllTestTest()

    class CheckIsNotMock : AfterAllCallback {
        override fun afterAll(context: ExtensionContext) {
            assertFalse(isMock(TestMock, objectMock = true))
        }
    }
}
