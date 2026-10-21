package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RurfTest {
    @Mock(relaxUnitFun = true)
    lateinit var mock1: RurfMockCls

    @Mock
    lateinit var mock2: RurfMockCls

    @Test
    fun rurfRegularOperationOk() {
        val mock =
            mock<RurfMockCls>(relaxUnitFun = true) {
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

    private fun mockCls() = mock<RurfMockCls>(relaxUnitFun = true)

    @Test
    fun rurfUnitOperationOkAnnotation1() {
        MockAnnotations.init(this)
        mock1.opUnit(1, 2)
    }

    @Test
    fun rurfUnitOperationOkAnnotation2() {
        MockAnnotations.init(this, relaxUnitFun = true)
        mock2.opUnit(1, 2)
    }
}
