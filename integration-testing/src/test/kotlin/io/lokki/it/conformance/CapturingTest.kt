package io.lokki.it.conformance

import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.slot
import io.lokki.verify
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapturingTest {
    private val mock = mock<MockedSubject>()

    @BeforeTest
    fun setup() {
        every { mock.doSomething("1", "data1") } returns "result1"
        every { mock.doSomething("2", "data2") } returns "result2"
    }

    @Test
    fun captureRegularSlot() {
        val mock = mock<MockCls>()

        val slot = CapturingSlot<Cls>()
        every { mock.op(1, 2, capture(slot)) } returns 22

        assertEquals(22, mock.op(1, 2, Cls().apply { value = 55 }))
        assertEquals(55, slot.captured.value)

        verify { mock.op(1, 2, any()) }
    }

    @Test
    fun `CapturingSlot can capture nullable value`() {
        val mock = mock<MockCls>()

        val slot = CapturingSlot<Cls?>()
        every { mock.nullableOp(1, 2, captureNullable(slot)) } returns 22

        assertEquals(22, mock.nullableOp(1, 2, null))
        assertNull(slot.captured?.value)

        verify { mock.nullableOp(1, 2, null) }
    }

    @Test
    fun `slot can capture nullable value`() {
        val mock = mock<MockCls>()

        val slot = slot<Cls?>()
        every { mock.nullableOp(1, 2, captureNullable(slot)) } returns 22

        assertEquals(22, mock.nullableOp(1, 2, null))
        assertNull(slot.captured?.value)

        verify { mock.nullableOp(1, 2, null) }
    }

    @Test
    fun `slot factory resolves for non-null and nullable type arguments`() {
        val nonNullSlot = slot<String>()
        nonNullSlot.captured = "value"
        assertEquals("value", nonNullSlot.captured)

        val nullableSlot = slot<String?>()
        nullableSlot.captured = null
        assertNull(nullableSlot.captured)
        assertTrue(nullableSlot.isNull)

        nullableSlot.captured = "also"
        assertEquals("also", nullableSlot.captured)
    }

    @Test
    fun `captureNullable can capture non null value`() {
        val mock = mock<MockCls>()

        val slot = slot<Cls?>()
        every { mock.nullableOp(1, 2, captureNullable(slot)) } returns 22
        val toBeCaptured = Cls()
        assertEquals(22, mock.nullableOp(1, 2, toBeCaptured))
        assertEquals(slot.captured?.value, toBeCaptured.value)

        verify { mock.nullableOp(1, 2, toBeCaptured) }
    }

    @Test
    fun captureListSlot() {
        val mock = mock<MockCls>()

        val items = mutableListOf<Cls>()
        every { mock.op(1, 2, capture(items)) } returns 22

        assertEquals(22, mock.op(1, 2, Cls().apply { value = 55 }))
        assertEquals(55, items[0].value)

        verify { mock.op(1, 2, any()) }
    }

    @Test
    fun coCaptureRegularSlot() {
        val mock = mock<CoMockCls>()

        val slot = CapturingSlot<Cls>()
        coEvery { mock.op(1, 2, capture(slot)) } returns 22

        runBlocking {
            assertEquals(22, mock.op(1, 2, Cls().apply { value = 55 }))
        }
        assertEquals(55, slot.captured.value)

        coVerify { mock.op(1, 2, any()) }
    }

    @Test
    fun coCaptureListSlot() {
        val mock = mock<CoMockCls>()

        val items = mutableListOf<Cls>()
        coEvery { mock.op(1, 2, capture(items)) } returns 22

        runBlocking {
            assertEquals(22, mock.op(1, 2, Cls().apply { value = 55 }))
        }
        assertEquals(55, items[0].value)

        coVerify { mock.op(1, 2, any()) }
    }

    @Test
    fun itThrowsALokkiExceptionWhenVerifyingTheSameFunctionTwiceWithSlots() {
        mock.doSomething("1", "data1")
        mock.doSomething("2", "data2")

        val dataSlotId1 = slot<String>()
        val dataSlotId2 = slot<String>()

        assertFailsWith<LokkiException> {
            verify {
                mock.doSomething(any(), capture(dataSlotId1))
                mock.doSomething(any(), capture(dataSlotId2))
            }
        }
    }

    @Test
    fun itDoesNotThrowALokkiExceptionWhenThereAreMultipleTestsVerifyingWithSlots() {
        mock.doSomething("1", "data1")

        val slot = slot<String>()
        verify {
            mock.doSomething("1", capture(slot))
        }

        assertEquals("data1", slot.captured)
    }

    @Test
    fun anotherTestToTestTheCoexistenceOfTestsWithSlots() {
        mock.doSomething("1", "data1")

        val slot = slot<String>()
        verify {
            mock.doSomething("1", capture(slot))
        }

        assertEquals("data1", slot.captured)
    }

    @Test
    fun itAllowsMultipleCapturingsOfTheSameFunctionUsingAMutableList() {
        mock.doSomething("1", "data1")
        mock.doSomething("2", "data2")

        val slotList = mutableListOf<String>()

        verify {
            mock.doSomething("1", capture(slotList))
            mock.doSomething("2", capture(slotList))
        }

        assertEquals(slotList.size, 2)
        assertEquals("data1", slotList[0])
        assertEquals("data2", slotList[1])
    }

    @Test
    fun testNullableCapture() {
        val list = mutableListOf<String?>()
        val test: MockNullableCls =
            mock {
                every { call(captureNullable(list)) } just Runs
            }

        val args = listOf("One", "Two", "Three", null)
        for (arg in args) {
            test.call(arg)
        }

        assertEquals(args, list)
    }

    @Test
    fun itDoesNotThrowALokkiExceptionWhenVerifyingTheSameFunctionTwiceWithSlotsWithDifferentMatchers() {
        mock.doSomething("1", "data1")
        mock.doSomething("2", "data2")

        val dataSlotId1 = slot<String>()
        val dataSlotId2 = slot<String>()

        verify {
            mock.doSomething("1", capture(dataSlotId1))
            mock.doSomething("2", capture(dataSlotId2))
        }

        assertEquals("data1", dataSlotId1.captured)
        assertEquals("data2", dataSlotId2.captured)
    }

    open class MockNullableCls {
        open fun call(unused: String?) {
            println(unused)
        }
    }

    class Cls {
        var value = 0
    }

    open class MockCls {
        open fun op(
            a: Int,
            b: Int,
            c: Cls,
        ) = a + b + c.value

        open fun nullableOp(
            a: Int,
            b: Int,
            c: Cls?,
        ): Int = a + b + (c?.value ?: 9)
    }

    open class CoMockCls {
        open suspend fun op(
            a: Int,
            b: Int,
            c: Cls,
        ): Int = coroutineScope { a + b + c.value }
    }

    open class MockedSubject {
        open fun doSomething(
            id: String?,
            data: Any?,
        ): String = throw IllegalStateException("Not mocked :(")
    }
}
