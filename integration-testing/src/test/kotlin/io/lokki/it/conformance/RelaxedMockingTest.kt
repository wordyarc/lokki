package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("UNUSED_PARAMETER")
class RelaxedMockingTest {
    open class MockCls {
        open fun op(
            a: Int,
            b: Int,
        ) = a + b

        open fun opUnit(
            a: Int,
            b: Int,
        ) {}
    }

    @Test
    fun rurfRegularOperationOk() {
        val mock =
            mock<MockCls>(relaxUnitFun = true) {
                every { op(1, 2) } returns 4
            }

        assertEquals(4, mock.op(1, 2))
    }

    @Test
    fun rurfRegularOperationFail() {
        val mock = mockCls()

        assertFailsWith<LokkiException> {
            assertEquals(4, mock.op(1, 2))
        }
    }

    @Test
    fun rurfUnitOperationOk() {
        val mock = mockCls()

        mock.opUnit(1, 2)
    }

    @Test
    fun rurfUnitOperationMocked() {
        val mock = mockCls()

        val slot = slot<Int>()
        every { mock.opUnit(1, capture(slot)) } just Runs

        mock.opUnit(1, 2)

        assertTrue(slot.isCaptured)
        assertEquals(2, slot.captured)
    }

    @Test
    fun testRelaxedFunction() {
        val block = mock<() -> Unit>(relaxed = true)
        block()
        verify { block.invoke() }
    }

    private fun mockCls() = mock<MockCls>(relaxUnitFun = true)
}
