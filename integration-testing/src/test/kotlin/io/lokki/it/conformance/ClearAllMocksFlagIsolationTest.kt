package io.lokki.it.conformance

import io.lokki.clearAllMocks
import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import io.lokki.mockObject
import io.lokki.spy
import io.lokki.unmockAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
class ClearAllMocksFlagIsolationTest {
    open class Target {
        open fun value() = 0
    }

    @AfterEach
    fun tearDown() = unmockAll()

    @Test
    fun `regularMocks flag only clears regular and spy mocks`() {
        val mock = mock<Target>(relaxed = true)
        val spy = spy(Target())
        val obj = ObjectTarget
        mockObject(obj)

        every { mock.value() } returns 10
        every { spy.value() } returns 20
        every { obj.value() } returns 30

        clearAllMocks(regularMocks = true, objectMocks = false, staticMocks = false, constructorMocks = false)

        assertEquals(0, mock.value())
        assertEquals(0, spy.value())
        assertEquals(30, obj.value())
    }

    @Test
    fun `objectMocks flag only clears object mocks`() {
        val mock = mock<Target>(relaxed = true)
        val obj = ObjectTarget
        mockObject(obj)

        every { mock.value() } returns 10
        every { obj.value() } returns 30

        clearAllMocks(regularMocks = false, objectMocks = true, staticMocks = false, constructorMocks = false)

        assertEquals(10, mock.value())
        assertEquals(0, obj.value())
    }

    @Test
    fun `no flags set clears nothing`() {
        val mock = mock<Target>(relaxed = true)
        val obj = ObjectTarget
        mockObject(obj)

        every { mock.value() } returns 10
        every { obj.value() } returns 30

        clearAllMocks(regularMocks = false, objectMocks = false, staticMocks = false, constructorMocks = false)

        assertEquals(10, mock.value())
        assertEquals(30, obj.value())
    }
}
