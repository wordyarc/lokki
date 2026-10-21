package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnnotationsTest {
    open class MockCls {
        open fun op(a: Int): Int = a + 1
    }

    class AnnotatedCls {
        @Mock
        lateinit var mock: MockCls

        @RelaxedMock
        lateinit var relaxedMock: MockCls

        @Spy
        var spy = MockCls()
    }

    @Test
    fun mock() {
        val obj = AnnotatedCls()

        MockAnnotations.init(obj)

        assertFailsWith(LokkiException::class) {
            obj.mock.op(1)
        }

        every { obj.mock.op(5) } returns 4

        assertEquals(4, obj.mock.op(5))

        verify { obj.mock.op(5) }
    }

    @Test
    fun relaxedMock() {
        val obj = AnnotatedCls()

        MockAnnotations.init(obj)

        assertEquals(0, obj.relaxedMock.op(5))

        verify { obj.relaxedMock.op(5) }
    }

    @Test
    fun spy() {
        val obj = AnnotatedCls()

        MockAnnotations.init(obj)

        assertEquals(6, obj.spy.op(5))

        verify { obj.spy.op(5) }
    }
}
