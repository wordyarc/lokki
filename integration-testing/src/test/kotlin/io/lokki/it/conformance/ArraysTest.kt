package io.lokki.it.conformance

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ArraysTest {
    val mock = mock<MockCls>()

    @Test
    fun booleanArray() {
        every { mock.arrayOp(BooleanArray(3) { true }) } returns BooleanArray(3) { false }

        assertContentEquals(BooleanArray(3) { false }, mock.arrayOp(BooleanArray(3) { true }))

        verify { mock.arrayOp(BooleanArray(3) { true }) }
    }

    @Test
    fun byteArray() {
        every { mock.arrayOp(ByteArray(3) { (it + 1).toByte() }) } returns ByteArray(3) { (3 - it).toByte() }

        assertContentEquals(ByteArray(3) { (3 - it).toByte() }, mock.arrayOp(ByteArray(3) { (it + 1).toByte() }))

        verify { mock.arrayOp(ByteArray(3) { (it + 1).toByte() }) }
    }

    @Test
    fun shortArray() {
        every { mock.arrayOp(ShortArray(3) { (it + 1).toShort() }) } returns ShortArray(3) { (3 - it).toShort() }

        assertContentEquals(ShortArray(3) { (3 - it).toShort() }, mock.arrayOp(ShortArray(3) { (it + 1).toShort() }))

        verify { mock.arrayOp(ShortArray(3) { (it + 1).toShort() }) }
    }

    @Test
    fun charArray() {
        every { mock.arrayOp(CharArray(3) { (it + 1).toChar() }) } returns CharArray(3) { (3 - it).toChar() }

        assertContentEquals(CharArray(3) { (3 - it).toChar() }, mock.arrayOp(CharArray(3) { (it + 1).toChar() }))

        verify { mock.arrayOp(CharArray(3) { (it + 1).toChar() }) }
    }

    @Test
    fun intArray() {
        every { mock.arrayOp(IntArray(3) { it + 1 }) } returns IntArray(3) { 3 - it }

        assertContentEquals(IntArray(3) { 3 - it }, mock.arrayOp(IntArray(3) { it + 1 }))

        verify { mock.arrayOp(IntArray(3) { it + 1 }) }
    }

    @Test
    fun longArray() {
        every { mock.arrayOp(LongArray(3) { (it + 1).toLong() }) } returns LongArray(3) { (3 - it).toLong() }

        assertContentEquals(LongArray(3) { (3 - it).toLong() }, mock.arrayOp(LongArray(3) { (it + 1).toLong() }))

        verify { mock.arrayOp(LongArray(3) { (it + 1).toLong() }) }
    }

    @Test
    fun floatArray() {
        every { mock.arrayOp(FloatArray(3) { (it + 1).toFloat() }) } returns FloatArray(3) { (3 - it).toFloat() }

        assertContentEquals(
            FloatArray(3) { (3 - it).toFloat() },
            mock.arrayOp(FloatArray(3) { (it + 1).toFloat() }),
        )

        verify { mock.arrayOp(FloatArray(3) { (it + 1).toFloat() }) }
    }

    @Test
    fun doubleArray() {
        every { mock.arrayOp(DoubleArray(3) { (it + 1).toDouble() }) } returns DoubleArray(3) { (3 - it).toDouble() }

        assertContentEquals(
            DoubleArray(3) { (3 - it).toDouble() },
            mock.arrayOp(DoubleArray(3) { (it + 1).toDouble() }),
        )

        verify { mock.arrayOp(DoubleArray(3) { (it + 1).toDouble() }) }
    }

    @Test
    fun booleanObjectArray() {
        every { mock.arrayOp(Array(3) { true }) } returns Array(3) { false }

        assertContentEquals(Array(3) { false }, mock.arrayOp(Array(3) { true }))

        verify { mock.arrayOp(Array(3) { true }) }
    }

    @Test
    fun byteObjectArray() {
        every { mock.arrayOp(Array(3) { (it + 1).toByte() }) } returns Array(3) { (3 - it).toByte() }

        assertContentEquals(Array(3) { (3 - it).toByte() }, mock.arrayOp(Array(3) { (it + 1).toByte() }))

        verify { mock.arrayOp(Array(3) { (it + 1).toByte() }) }
    }

    @Test
    fun shortObjectArray() {
        every { mock.arrayOp(Array(3) { (it + 1).toShort() }) } returns Array(3) { (3 - it).toShort() }

        assertContentEquals(
            Array(3) { (3 - it).toShort() },
            mock.arrayOp(Array(3) { (it + 1).toShort() }),
        )

        verify { mock.arrayOp(Array(3) { (it + 1).toShort() }) }
    }

    @Test
    fun charObjectArray() {
        every { mock.arrayOp(Array(3) { (it + 1).toChar() }) } returns Array(3) { (3 - it).toChar() }

        assertContentEquals(Array(3) { (3 - it).toChar() }, mock.arrayOp(Array(3) { (it + 1).toChar() }))

        verify { mock.arrayOp(Array(3) { (it + 1).toChar() }) }
    }

    @Test
    fun intObjectArray() {
        every { mock.arrayOp(Array(3) { it + 1 }) } returns Array(3) { 3 - it }

        assertContentEquals(Array(3) { 3 - it }, mock.arrayOp(Array(3) { it + 1 }))

        verify { mock.arrayOp(Array(3) { it + 1 }) }
    }

    @Test
    fun longObjectArray() {
        every { mock.arrayOp(Array(3) { (it + 1).toLong() }) } returns Array(3) { (3 - it).toLong() }

        assertContentEquals(Array(3) { (3 - it).toLong() }, mock.arrayOp(Array(3) { (it + 1).toLong() }))

        verify { mock.arrayOp(Array(3) { (it + 1).toLong() }) }
    }

    @Test
    fun floatObjectArray() {
        every { mock.arrayOp(Array(3) { (it + 1).toFloat() }) } returns Array(3) { (3 - it).toFloat() }

        assertContentEquals(
            Array(3) { (3 - it).toFloat() },
            mock.arrayOp(Array(3) { (it + 1).toFloat() }),
        )

        verify { mock.arrayOp(Array(3) { (it + 1).toFloat() }) }
    }

    @Test
    fun doubleObjectArray() {
        every {
            mock.arrayOp(Array(3) { (it + 1).toDouble() })
        } returns Array(3) { (3 - it).toDouble() }

        assertContentEquals(
            Array(3) { (3 - it).toDouble() },
            mock.arrayOp(Array(3) { (it + 1).toDouble() }),
        )

        verify { mock.arrayOp(Array(3) { (it + 1).toDouble() }) }
    }

    @Test
    fun anyAnyObjectArray() {
        every {
            mock.arrayOp(Array(3) { i -> Array(3) { j -> i + j } })
        } returns Array(3) { i -> Array(3) { j -> j - i } }

        assertTrue(
            Array(3) { i -> Array<Any>(3) { j -> j - i } }
                contentDeepEquals mock.arrayOp(Array(3) { i -> Array(3) { j -> i + j } }),
        )

        verify { mock.arrayOp(Array(3) { i -> Array(3) { j -> i + j } }) }
    }

    @Test
    fun intWrapperObjectArray() {
        every { mock.arrayOp(any<Array<IntWrapper>>()) } answers { Array(3) { IntWrapper(it + 2) } }

        assertContentEquals(
            Array(3) { IntWrapper(it + 2) },
            mock.arrayOp(Array(3) { IntWrapper(it + 5) }),
        )

        verify { mock.arrayOp(Array(3) { IntWrapper(it + 5) }) }
    }

    data class IntWrapper(
        val data: Int,
    )

    interface MockCls {
        fun arrayOp(arr: BooleanArray): BooleanArray

        fun arrayOp(arr: ByteArray): ByteArray

        fun arrayOp(arr: ShortArray): ShortArray

        fun arrayOp(arr: CharArray): CharArray

        fun arrayOp(arr: IntArray): IntArray

        fun arrayOp(arr: LongArray): LongArray

        fun arrayOp(arr: FloatArray): FloatArray

        fun arrayOp(arr: DoubleArray): DoubleArray

        fun arrayOp(arr: Array<Boolean>): Array<Boolean>

        fun arrayOp(arr: Array<Byte>): Array<Byte>

        fun arrayOp(arr: Array<Short>): Array<Short>

        fun arrayOp(arr: Array<Char>): Array<Char>

        fun arrayOp(arr: Array<Int>): Array<Int>

        fun arrayOp(arr: Array<Long>): Array<Long>

        fun arrayOp(arr: Array<Float>): Array<Float>

        fun arrayOp(arr: Array<Double>): Array<Double>

        fun arrayOp(array: Array<Array<Any>>): Array<Array<Any>>

        fun arrayOp(array: Array<IntWrapper>): Array<IntWrapper>
    }
}
