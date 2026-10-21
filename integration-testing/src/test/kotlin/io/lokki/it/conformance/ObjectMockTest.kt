package io.lokki.it.conformance

import io.lokki.every
import io.lokki.junit5.LokkiExtension
import io.lokki.mockObject
import io.lokki.verify
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
class ObjectMockTest {
    @Test
    fun objectMockByDefault() {
        mockObject(MockObj)
        assertEquals(3, MockObj.add(1, 2))
    }

    @Test
    fun objectMockWithDefinedBehaviour() {
        mockObject(MockObj)
        every { MockObj.add(1, 2) } returns 55
        assertEquals(55, MockObj.add(1, 2))
    }

    @Test
    fun objectMockCleared() {
        mockObject(MockObj)
        every { MockObj.add(1, 2) } returns 55
        assertEquals(55, MockObj.add(1, 2))
        verify { MockObj.add(1, 2) }

        mockObject(MockObj)

        verify(exactly = 0) { MockObj.add(1, 2) }
        every { MockObj.add(1, 2) } returns 33
        assertEquals(33, MockObj.add(1, 2))
        verify { MockObj.add(1, 2) }
    }
}
