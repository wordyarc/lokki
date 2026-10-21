package io.lokki.it.conformance

import io.lokki.InjectMocks
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
import kotlin.test.assertNotNull
import kotlin.test.assertSame

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

    class InjectionTarget1(
        val obj: MockCls,
    )

    class InjectionTarget2 {
        lateinit var obj: MockCls
    }

    open class InjectionTarget4 {
        lateinit var obj: MockCls

        open fun op(a: Int): Int = obj.op(a)
    }

    class InjectSourceCls {
        @Mock
        lateinit var mock: MockCls

        @InjectMocks
        lateinit var target1: InjectionTarget1

        @InjectMocks
        lateinit var target2: InjectionTarget2
    }

    @Test
    fun inject() {
        val source = InjectSourceCls()

        MockAnnotations.init(source)

        assertNotNull(source.mock)

        assertSame(source.mock, source.target1.obj)
        assertSame(source.mock, source.target2.obj)
    }

    class InjectIntoSpySourceCls {
        @Mock
        lateinit var mock: MockCls

        @InjectMocks
        @Spy
        lateinit var target4: InjectionTarget4
    }

    @Test
    fun injectIntoSpy() {
        val callValue = 5
        val source = InjectIntoSpySourceCls()
        MockAnnotations.init(source)

        every { source.mock.op(any()) } returns callValue
        assertNotNull(source.target4)
        assertEquals(callValue, source.target4.op(0))
        verify { source.target4.op(any()) }
    }
}
