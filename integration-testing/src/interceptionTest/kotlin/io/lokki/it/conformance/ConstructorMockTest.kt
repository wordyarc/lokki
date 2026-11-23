package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.clearConstructorMock
import io.lokki.every
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.unmockAll
import io.lokki.unmockConstructor
import io.lokki.verify
import io.lokki.junit5.LokkiExtension
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(LokkiExtension::class)
class ConstructorMockTest {
    @Test
    fun test1() {
        mockConstructor(ConstructedExample::class)

        every { anyConstructed<ConstructedExample>().exampleProperty } returns 0

        assertEquals(0, ConstructedExample().exampleProperty)
    }

    @Test
    fun unmockAllTest() {
        mockConstructor(ConstructedExample::class)
        mockConstructor(ConstructedExample::class)

        every { anyConstructed<ConstructedExample>().exampleProperty } returns 0

        assertEquals(0, ConstructedExample().exampleProperty)

        unmockAll()

        assertEquals(1, ConstructedExample().exampleProperty)

        assertFailsWith<LokkiException> {
            every { anyConstructed<ConstructedExample>().exampleProperty } returns 0
        }
    }

    @Test
    fun simpleTest() {
        mockConstructor(ConstructedCls::class)

        every { anyConstructed<ConstructedCls>().op(1, 2) } returns 4

        assertEquals(4, ConstructedCls().op(1, 2))

        verify { anyConstructed<ConstructedCls>().op(1, 2) }
    }

    @Test
    fun cleanAfter() {
        mockConstructor(ConstructedCls::class)

        every { anyConstructed<ConstructedCls>().op(1, 2) } returns 4

        clearConstructorMock(ConstructedCls::class)

        assertEquals(3, ConstructedCls().op(1, 2))
    }

    @Test
    fun instanceCleanAfter() {
        mockConstructor(ConstructedCls::class)

        every { anyConstructed<ConstructedCls>().op(1, 2) } returns 4

        val instance = ConstructedCls()

        unmockConstructor(ConstructedCls::class)

        assertEquals(3, instance.op(1, 2))
    }

    @Test
    fun clear() {
        mockConstructor(ConstructedCls::class)

        every { anyConstructed<ConstructedCls>().op(1, 2) } returns 4

        assertEquals(4, ConstructedCls().op(1, 2))

        verify { anyConstructed<ConstructedCls>().op(1, 2) }

        clearConstructorMock(ConstructedCls::class)

        verify(exactly = 0) { anyConstructed<ConstructedCls>() }

        assertEquals(3, ConstructedCls().op(1, 2))

        verify { anyConstructed<ConstructedCls>().op(1, 2) }
    }

    @Test
    fun fakeConstructor() {
        mockConstructor(ConstructedCls::class)

        every { ConstructedCls().op(1, 2) } returns 4

        assertEquals(4, ConstructedCls().op(1, 2))

        verify { ConstructedCls().op(1, 2) }
    }

    @Test
    fun fakeConstructor2() {
        mockConstructor(ConstructedCls::class)

        every { ConstructedCls(5).op(1, 2) } returns 4

        assertEquals(4, ConstructedCls(5).op(1, 2))

        verify { ConstructedCls(5).op(1, 2) }
    }

    @Test
    fun constructedWith() {
        mockConstructor(ConstructedCls::class)

        every {
            constructedWith<ConstructedCls>(ofType<String>()).op(any(), any())
        } returns 23
        every {
            constructedWith<ConstructedCls>(eq(6)).op(any(), any())
        } returns 55
        every {
            constructedWith<ConstructedCls>(ofType<Int>()).op(any(), any())
        } returns 35

        assertEquals(23, ConstructedCls("5").op(1, 2))
        assertEquals(35, ConstructedCls(5).op(1, 2))
        assertEquals(55, ConstructedCls(6).op(1, 2))

        verify {
            constructedWith<ConstructedCls>(eq(6)).op(1, 2)
        }
    }

    @Test
    fun constructedWithSameInstanceMatcher() {
        mockConstructor(ConstructedWithRef::class)

        val tokenProvider = mock<ConstructedTokenProvider>()
        
        every {
            constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProvider)).op()
        } returns "ok"

        assertEquals("ok", ConstructedWithRef("ACTION", tokenProvider).op())

        verify {
            constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProvider)).op()
        }
    }

    @Test
    fun constructedWithSameInstanceMatcherDistinguishesArgs() {
        mockConstructor(ConstructedWithRef::class)

        val tokenProviderA = mock<ConstructedTokenProvider>()
        val tokenProviderB = mock<ConstructedTokenProvider>()

        every {
            constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProviderA)).op()
        } returns "a"
        every {
            constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProviderB)).op()
        } returns "b"

        assertEquals("a", ConstructedWithRef("ACTION", tokenProviderA).op())
        assertEquals("b", ConstructedWithRef("ACTION", tokenProviderB).op())

        verify { constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProviderA)).op() }
        verify { constructedWith<ConstructedWithRef>(eq("ACTION"), refEq(tokenProviderB)).op() }
    }

    @Test
    fun `constructedWith() using default constructor params`() {
        mockConstructor(ConstructedCls::class)

        every { constructedWith<ConstructedCls>().op(1, 3) } returns 33

        assertEquals(33, ConstructedCls().op(1, 3))

        verify {
            constructedWith<ConstructedCls>().op(1, 3)
        }
    }

    @Test
    fun unmockAllconstructedWith() {
        mockConstructor(ConstructedCls::class)
        mockConstructor(ConstructedCls::class)

        val checkConstructedWith = { a: Int, b: Int, c: Int ->
            every {
                constructedWith<ConstructedCls>(ofType<String>()).op(any(), any())
            } returns a
            every {
                constructedWith<ConstructedCls>(eq(6)).op(any(), any())
            } returns b
            every {
                constructedWith<ConstructedCls>(ofType<Int>()).op(any(), any())
            } returns c

            assertEquals(a, ConstructedCls("5").op(1, 2))
            assertEquals(b, ConstructedCls(6).op(1, 2))
            assertEquals(c, ConstructedCls(5).op(1, 2))
        }

        checkConstructedWith(23, 55, 35)

        mockConstructor(ConstructedCls::class)
        checkConstructedWith(44, 101, 42)

        unmockAll()

        assertEquals(8, ConstructedCls("5").op(1, 2))
        assertEquals(8, ConstructedCls(5).op(1, 2))
        assertEquals(9, ConstructedCls(6).op(1, 2))

        assertFailsWith<LokkiException> {
            every {
                constructedWith<ConstructedCls>(ofType<String>()).op(any(), any())
            } returns 23
        }
    }

    @Test
    fun returnObject() {
        mockConstructor(ConstructedCls::class)

        every { ConstructedCls().opList(1, 2) } returns listOf(5, 6)

        assertEquals(listOf(5, 6), ConstructedCls().opList(1, 2))

        verify { ConstructedCls().opList(1, 2) }
    }

    @Test
    fun chainedOps() {
        mockConstructor(ConstructedCls::class)

        every { ConstructedCls().chainOp(1, 2).chainOp(3, 4).op(5, 6) } returns 7

        assertEquals(7, ConstructedCls().chainOp(1, 2).chainOp(3, 4).op(5, 6))

        verify { ConstructedCls().chainOp(1, 2).chainOp(3, 4).op(5, 6) }

        clearConstructorMock(ConstructedCls::class)

        assertEquals(21, ConstructedCls().chainOp(1, 2).chainOp(3, 4).op(5, 6))
    }
}
