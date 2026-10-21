package io.lokki.it.conformance

import io.lokki.LokkiException
import io.lokki.coEvery
import io.lokki.every
import io.lokki.mock
import io.lokki.slot
import io.lokki.spy
import io.lokki.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ValueClassTest {
    private val dummyValueWrapperArg get() = DummyValueWrapper(DummyValue(42))
    private val dummyValueWrapperReturn get() = DummyValueWrapper(DummyValue(99))
    private val complexValueParam = UUID.fromString("4d19b22c-7754-4c55-ba4d-f80109708a1f")
    private val complexValueResultArg get() = ComplexValue(complexValueParam)
    private val complexValueResultReturn get() = Result.success(complexValueResultArg)

    private val dummyValueClassArg get() = DummyValue(101)
    private val dummyComplexValueClassArg get() = ComplexValue(UUID.fromString("4d19b22c-7754-4c55-ba4d-f80109708a1f"))
    private val dummyValueClassReturn get() = DummyValue(202)
    private val dummyComplexValueClassReturn get() = ComplexValue(UUID.fromString("25581db2-4cdb-48cd-a6c9-e087aee31f0b"))

    @Test
    fun `arg is ValueClass, returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(dummyValueClassArg) } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.argValueClassReturnValueClass(dummyValueClassArg))

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `field is ValueClass, returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { valueClassField } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.valueClassField)

        verify { mock.valueClassField }
    }

    @Test
    fun `arg is any(ValueClass), returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(any()) } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.argValueClassReturnValueClass(dummyValueClassArg))

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is eq(ValueClass), returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(eq(dummyValueClassArg)) } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.argValueClassReturnValueClass(dummyValueClassArg))

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is slot(ValueClass), returns ValueClass`() {
        val slot = slot<DummyValue>()
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(capture(slot)) } returns dummyValueClassReturn
            }

        val result = mock.argValueClassReturnValueClass(dummyValueClassArg)

        assertEquals(dummyValueClassReturn, result)

        assertEquals(dummyValueClassArg, slot.captured)

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is MutableList(ValueClass), returns ValueClass`() {
        val slot = mutableListOf<DummyValue>()
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(capture(slot)) } returns dummyValueClassReturn
            }

        val result = mock.argValueClassReturnValueClass(dummyValueClassArg)

        assertEquals(dummyValueClassReturn, result)

        assertEquals(dummyValueClassArg, slot.single())

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is ValueClass, answers ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(dummyValueClassArg) } answers { dummyValueClassReturn }
            }

        assertEquals(dummyValueClassReturn, mock.argValueClassReturnValueClass(dummyValueClassArg))

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is any(ValueClass), answers ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(any()) } answers { dummyValueClassReturn }
            }

        assertEquals(dummyValueClassReturn, mock.argValueClassReturnValueClass(dummyValueClassArg))

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is slot(ValueClass), answers ValueClass`() {
        val slot = slot<DummyValue>()

        val mock =
            mock<DummyService> {
                every { argValueClassReturnValueClass(capture(slot)) } answers { dummyValueClassReturn }
            }

        val result = mock.argValueClassReturnValueClass(dummyValueClassArg)

        assertEquals(dummyValueClassReturn, result)

        assertEquals(dummyValueClassArg, slot.captured)

        verify { mock.argValueClassReturnValueClass(dummyValueClassArg) }
    }

    @Test
    fun `arg is ComplexValueClass, returns ComplexValueClass`() {
        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) } returns dummyComplexValueClassReturn
            }

        assertEquals(dummyComplexValueClassReturn, mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg))

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is any(ComplexValueClass), returns ComplexValueClass`() {
        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(any()) } returns dummyComplexValueClassReturn
            }

        assertEquals(dummyComplexValueClassReturn, mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg))

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is slot(ComplexValueClass), returns ComplexValueClass`() {
        val slot = slot<ComplexValue>()
        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(capture(slot)) } returns dummyComplexValueClassReturn
            }

        val result = mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg)

        assertEquals(dummyComplexValueClassReturn, result)

        assertEquals(dummyComplexValueClassArg, slot.captured)

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is ComplexValueClass, answers ComplexValueClass`() {
        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) } answers { dummyComplexValueClassReturn }
            }

        assertEquals(dummyComplexValueClassReturn, mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg))

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is any(ComplexValueClass), answers ComplexValueClass`() {
        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(any()) } answers { dummyComplexValueClassReturn }
            }

        assertEquals(dummyComplexValueClassReturn, mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg))

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is slot(ComplexValueClass), answers ComplexValueClass`() {
        val slot = slot<ComplexValue>()

        val mock =
            mock<DummyService> {
                every { argComplexValueClassReturnComplexValueClass(capture(slot)) } answers { dummyComplexValueClassReturn }
            }

        val result = mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg)

        assertEquals(dummyComplexValueClassReturn, result)

        assertEquals(dummyComplexValueClassArg, slot.captured)

        verify { mock.argComplexValueClassReturnComplexValueClass(dummyComplexValueClassArg) }
    }

    @Test
    fun `arg is ValueClass, returns Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(dummyValueClassArg) } returns dummyValueWrapperReturn
            }

        assertEquals(dummyValueWrapperReturn, mock.argValueClassReturnWrapper(dummyValueClassArg))

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is any(ValueClass), returns Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(any()) } returns dummyValueWrapperReturn
            }

        assertEquals(dummyValueWrapperReturn, mock.argValueClassReturnWrapper(dummyValueClassArg))

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is slot(ValueClass), returns Wrapper`() {
        val slot = slot<DummyValue>()

        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(capture(slot)) } returns dummyValueWrapperReturn
            }

        val result = mock.argValueClassReturnWrapper(dummyValueClassArg)

        assertEquals(dummyValueWrapperReturn, result)

        assertEquals(dummyValueClassArg, slot.captured)

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is ValueClass, answers Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(dummyValueClassArg) } answers { dummyValueWrapperReturn }
            }

        assertEquals(dummyValueWrapperReturn, mock.argValueClassReturnWrapper(dummyValueClassArg))

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is any(ValueClass), answers Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(any()) } answers { dummyValueWrapperReturn }
            }

        assertEquals(dummyValueWrapperReturn, mock.argValueClassReturnWrapper(dummyValueClassArg))

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is slot(ValueClass), answers Wrapper`() {
        val slot = slot<DummyValue>()

        val mock =
            mock<DummyService> {
                every { argValueClassReturnWrapper(capture(slot)) } answers { dummyValueWrapperReturn }
            }

        val result = mock.argValueClassReturnWrapper(dummyValueClassArg)

        assertEquals(dummyValueWrapperReturn, result)

        assertEquals(dummyValueClassArg, slot.captured)

        verify { mock.argValueClassReturnWrapper(dummyValueClassArg) }
    }

    @Test
    fun `arg is Wrapper, returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(dummyValueWrapperArg) } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.argWrapperReturnValueClass(dummyValueWrapperArg))

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is any(Wrapper), returns ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(any()) } returns dummyValueClassReturn
            }

        assertEquals(dummyValueClassReturn, mock.argWrapperReturnValueClass(dummyValueWrapperArg))

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is slot(Wrapper), returns ValueClass`() {
        val slot = slot<DummyValueWrapper>()
        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(capture(slot)) } returns dummyValueClassReturn
            }

        val result = mock.argWrapperReturnValueClass(dummyValueWrapperArg)

        assertEquals(dummyValueClassReturn, result)

        assertEquals(dummyValueWrapperArg, slot.captured)

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is Wrapper, answers ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(dummyValueWrapperArg) } answers { dummyValueClassReturn }
            }

        assertEquals(dummyValueClassReturn, mock.argWrapperReturnValueClass(dummyValueWrapperArg))

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is any(Wrapper), answers ValueClass`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(any()) } answers { dummyValueClassReturn }
            }

        assertEquals(dummyValueClassReturn, mock.argWrapperReturnValueClass(dummyValueWrapperArg))

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is slot(Wrapper), answers ValueClass`() {
        val slot = slot<DummyValueWrapper>()

        val mock =
            mock<DummyService> {
                every { argWrapperReturnValueClass(capture(slot)) } answers { dummyValueClassReturn }
            }

        val result = mock.argWrapperReturnValueClass(dummyValueWrapperArg)

        assertEquals(dummyValueClassReturn, result)

        assertEquals(dummyValueWrapperArg, slot.captured)

        verify { mock.argWrapperReturnValueClass(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is Wrapper, returns Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(dummyValueWrapperArg) } returns dummyValueWrapperReturn
            }

        assertEquals(dummyValueWrapperReturn, mock.argWrapperReturnWrapper(dummyValueWrapperArg))

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is any(Wrapper), returns Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(any()) } returns dummyValueWrapperReturn
            }

        assertEquals(dummyValueWrapperReturn, mock.argWrapperReturnWrapper(dummyValueWrapperArg))

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is slot(Wrapper), returns Wrapper`() {
        val slot = slot<DummyValueWrapper>()
        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(capture(slot)) } returns dummyValueWrapperReturn
            }

        val result = mock.argWrapperReturnWrapper(dummyValueWrapperArg)

        assertEquals(dummyValueWrapperReturn, result)

        assertEquals(dummyValueWrapperArg, slot.captured)

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is Wrapper, answers Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(dummyValueWrapperArg) } answers { dummyValueWrapperReturn }
            }

        assertEquals(dummyValueWrapperReturn, mock.argWrapperReturnWrapper(dummyValueWrapperArg))

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is any(Wrapper), answers Wrapper`() {
        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(any()) } answers { dummyValueWrapperReturn }
            }

        assertEquals(dummyValueWrapperReturn, mock.argWrapperReturnWrapper(dummyValueWrapperArg))

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `arg is slot(Wrapper), answers Wrapper`() {
        val slot = slot<DummyValueWrapper>()

        val mock =
            mock<DummyService> {
                every { argWrapperReturnWrapper(capture(slot)) } answers { dummyValueWrapperReturn }
            }

        val result = mock.argWrapperReturnWrapper(dummyValueWrapperArg)

        assertEquals(dummyValueWrapperReturn, result)

        assertEquals(dummyValueWrapperArg, slot.captured)

        verify { mock.argWrapperReturnWrapper(dummyValueWrapperArg) }
    }

    @Test
    fun `any Wrapper does not match unrelated value class with same underlying type`() {
        val mock =
            mock<DummyService> {
                every { argAnyReturnString(any<DummyValueWrapper>()) } returns "wrapper"
            }

        assertEquals("wrapper", mock.argAnyReturnString(dummyValueWrapperArg))

        assertFailsWith<LokkiException> {
            mock.argAnyReturnString(dummyValueClassArg)
        }
        assertFailsWith<LokkiException> {
            mock.argAnyReturnString(dummyValueClassArg.value)
        }
    }

    @Test
    fun `arg is captureNullable list Wrapper, returns String`() {
        val captured = mutableListOf<DummyValueWrapper?>()
        val mock =
            mock<DummyService> {
                every { argNullableWrapperReturnString(captureNullable(captured)) } returns "wrapper"
            }

        assertEquals("wrapper", mock.argNullableWrapperReturnString(dummyValueWrapperArg))
        assertEquals("wrapper", mock.argNullableWrapperReturnString(null))
        assertEquals(listOf(dummyValueWrapperArg, null), captured)
    }

    @Test
    fun `arg None, returns UInt`() {
        val mock =
            mock<DummyService> {
                every { argNoneReturnsUInt() } returns 999u
            }

        val result = mock.argNoneReturnsUInt()

        assertEquals(999u, result)
    }

    @Test
    fun `arg None, answers UInt`() {
        val mock =
            mock<DummyService> {
                every { argNoneReturnsUInt() } answers { 999u }
            }

        val result = mock.argNoneReturnsUInt()

        assertEquals(999u, result)
    }

    @Test
    fun `receiver is String, return is ValueClass`() {
        val fn = mock<String.() -> DummyValue>()

        every { "string".fn() } returns dummyValueClassReturn

        val result = "string".fn()

        assertEquals(dummyValueClassReturn, result)
    }

    @Test
    fun `receiver is String, return is Wrapper`() {
        val fn = mock<String.() -> DummyValueWrapper>()

        every { "string".fn() } returns dummyValueWrapperArg

        val result = "string".fn()

        assertEquals(dummyValueWrapperArg, result)
    }

    @Test
    fun `receiver is Wrapper, return is Wrapper`() {
        val fn = mock<DummyValueWrapper.() -> DummyValueWrapper>()

        every { dummyValueWrapperArg.fn() } returns dummyValueWrapperArg

        val result = dummyValueWrapperArg.fn()

        assertEquals(dummyValueWrapperArg, result)
    }

    @Test
    fun `receiver is Wrapper, return is ValueClass`() {
        val fn = mock<DummyValueWrapper.() -> DummyValue>()

        every { dummyValueWrapperArg.fn() } returns dummyValueClassReturn

        val result = dummyValueWrapperArg.fn()

        assertEquals(dummyValueClassReturn, result)
    }

    @Test
    fun `receiver is Wrapper, return is String`() {
        val fn = mock<DummyValueWrapper.() -> String>()

        every { dummyValueWrapperArg.fn() } returns "example"

        val result = dummyValueWrapperArg.fn()

        assertEquals("example", result)
    }

    @Test
    fun `receiver is ComplexValue, return is Result with ComplexValue`() {
        val service = mock<DummyService>()

        every { service.argGenericValueClassReturnResultWrapped(complexValueResultArg) } returns complexValueResultReturn

        val result = service.argGenericValueClassReturnResultWrapped(complexValueResultArg)

        assertEquals(complexValueResultReturn.getOrNull()?.value, result.getOrNull()?.value)
        assertEquals(complexValueResultReturn.getOrNull(), result.getOrNull())
    }

    @Test
    fun `receiver is ValueClass, return is Wrapper`() {
        val fn = mock<DummyValue.() -> DummyValueWrapper>()

        every { dummyValueClassArg.fn() } returns dummyValueWrapperReturn

        val result = dummyValueClassArg.fn()

        assertEquals(dummyValueWrapperReturn, result)
    }

    @Test
    fun `receiver is ValueClass, return is ValueClass`() {
        val fn = mock<DummyValue.() -> DummyValue>()

        every { dummyValueClassArg.fn() } returns dummyValueClassReturn

        val result = dummyValueClassArg.fn()

        assertEquals(dummyValueClassReturn, result)
    }

    @Test
    fun `function returning nullable value class not boxed due to cast to another type`() {
        val mock =
            mock<DummyService> {
                every { nullableValueClass() } returns DummyValue(2)
            }

        assertEquals(DummyValue(2), mock.nullableValueClass())
    }

    @Test
    fun `function returning nullable complex value class, returns value class`() {
        val expected = ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3"))
        val mock =
            mock<DummyService> {
                every { nullableComplexValueClass() } returns expected
            }

        assertEquals(expected, mock.nullableComplexValueClass())
    }

    @Test
    fun `function returning nullable nested value class, returns value class`() {
        val mock =
            mock<DummyService> {
                every { nullableNestedValueClass() } returns DummyValueWrapper(DummyValue(10))
            }

        assertEquals(DummyValueWrapper(DummyValue(10)), mock.nullableNestedValueClass())
    }

    @Test
    fun `function returning nullable nested complex value class, returns value class`() {
        val expected = ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3"))

        val mock =
            mock<DummyService> {
                every { nullableNestedComplexValueClass() } returns DummyComplexValueWrapper(expected)
            }

        assertEquals(DummyComplexValueWrapper(expected), mock.nullableNestedComplexValueClass())
    }

    @Test
    fun `nullable complex value class field, returns value class`() {
        val expected = ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3"))
        val mock =
            mock<DummyService> {
                every { nullableComplexValueClassField } returns expected
            }

        assertEquals(expected, mock.nullableComplexValueClassField)
    }

    @Test
    fun `nullable nested value class field, returns value class`() {
        val mock =
            mock<DummyService> {
                every { nullableNestedValueClassField } returns DummyValueWrapper(DummyValue(10))
            }

        assertEquals(DummyValueWrapper(DummyValue(10)), mock.nullableNestedValueClassField)
    }

    @Test
    fun `nullable nested complex value class field, returns value class`() {
        val expected = ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3"))
        val mock =
            mock<DummyService> {
                every { nullableNestedComplexValueClassField } returns DummyComplexValueWrapper(expected)
            }

        assertEquals(DummyComplexValueWrapper(expected), mock.nullableNestedComplexValueClassField)
    }

    @Test
    fun `nullable value class field is not boxed due to cast to another type`() {
        val mock =
            mock<DummyService> {
                every { nullableValueClassField } returns DummyValue(2)
            }

        assertEquals(DummyValue(2), mock.nullableValueClassField)
    }

    @Test
    fun `receiver is ValueClass, return is String`() {
        val fn = mock<DummyValue.() -> String>()

        every { dummyValueClassArg.fn() } returns "example"

        val result = dummyValueClassArg.fn()

        assertEquals("example", result)
    }

    @Test
    fun `result value`() {
        val givenResult = DummyValue(42)

        val mock =
            mock<DummyService> {
                every { returnValueClass() } returns givenResult
            }

        val result = mock.returnValueClass()

        assertEquals(givenResult, result)
    }

    @Test
    fun `ensure no infinite recursion when mocking fun that returns value class`() {
        val f: () -> DummyValue = mock()

        assertTimeoutPreemptively(Duration.ofMillis(500L)) {
            runCatching {
                every { f.invoke() } returns DummyValue(42)
            }
        }
    }

    @Test
    fun `nullable value class does not hang or cause OutOfMemoryError when returning value`() {
        assertTimeoutPreemptively(Duration.ofSeconds(3)) {
            val mock =
                mock<ClassProvider> {
                    every { provideTestClass() } returns ValueTestClass(4)
                }
            assertEquals(ValueTestClass(4), mock.provideTestClass())
        }
    }

    @Test
    fun `nullable value class does not hang or cause OutOfMemoryError when return null`() {
        assertTimeoutPreemptively(Duration.ofSeconds(3)) {
            val mock =
                mock<ClassProvider> {
                    every { provideTestClass() } returns null
                }
            assertNull(mock.provideTestClass())
        }
    }

    @Test
    fun `nullable non-primitive value class does not hang or cause OutOfMemoryError when returning value`() {
        assertTimeoutPreemptively(Duration.ofSeconds(3)) {
            val mock =
                mock<NonPrimitiveClassProvider> {
                    every { provideTestClass() } returns NonPrimitiveValueTestClass("4")
                }
            assertEquals(NonPrimitiveValueTestClass("4"), mock.provideTestClass())
        }
    }

    @Test
    fun `nullable non-primitive value class does not hang or cause OutOfMemoryError when return null`() {
        assertTimeoutPreemptively(Duration.ofSeconds(3)) {
            val mock =
                mock<NonPrimitiveClassProvider> {
                    every { provideTestClass() } returns null
                }
            assertNull(mock.provideTestClass())
        }
    }

    @Test
    fun `nullable value class verification with exact value - similar to nishatoma example`() {
        val mock = mock<ValueClassWithOptionalReturn>()
        every { mock.something(any()) } returns ValueClassId("bar")

        mock.something(ValueClassId("foo"))

        verify { mock.something(ValueClassId("foo")) }
    }

    @Test
    fun `matcher for nullable Boolean value class stays on its own argument`() {
        repeat(20) {
            val mock = mock<NullableBooleanValueConsumer>(relaxed = true)

            mock.consume(BooleanValue(false), BooleanValue(true))

            verify { mock.consume(any(), BooleanValue(true)) }
            verify(exactly = 0) { mock.consume(BooleanValue(true), any()) }
        }
    }

    @Test
    fun `spy class returning value class not boxed due to cast to another type`() {
        val f = spy<DummyService>()
        val result = f.returnValueClassNotInlined() as DummyValue

        assertEquals(DummyValue(0), result)
    }

    @Test
    fun `mock class returning value class not boxed due to cast to another type`() {
        val f = mock<DummyService>()
        every { f.returnValueClassNotInlined() } returns DummyValue(3)
        val result = f.returnValueClassNotInlined() as DummyValue

        assertEquals(DummyValue(3), result)
    }

    @Test
    fun `spy class returning value class boxed due to suspend function`() {
        val f = spy<DummyService>()
        val result = runBlocking { f.returnValueClassSuspendNotInlined() }

        assertEquals(DummyValue(0), result)
    }

    @Test
    fun `mock class returning value class boxed due to suspend function`() {
        val f = mock<DummyService>()
        coEvery { f.returnValueClassSuspendNotInlined() } returns DummyValue(3)
        val result = runBlocking { f.returnValueClassSuspendNotInlined() }

        assertEquals(DummyValue(3), result)
    }

    @Test
    fun `mock class returning nested value class boxed due to suspend function`() {
        val expected = DummyValueWrapper(DummyValue(3))
        val f = mock<DummyService>()
        coEvery { f.returnNestedValueClassSuspendNotInlined() } returns expected
        val result = runBlocking { f.returnNestedValueClassSuspendNotInlined() }

        assertEquals(expected, result)
    }

    @Test
    fun `mock class returning nested complex value class not boxed due to suspend function`() {
        val expected =
            DummyComplexValueWrapper(ComplexValue(UUID.fromString("bca61f8d-ba4d-475f-8dc6-08b943836998")))
        val f = mock<DummyService>()
        coEvery { f.returnNestedComplexValueClassSuspendNotInlined() } returns expected
        val result = runBlocking { f.returnNestedComplexValueClassSuspendNotInlined() }

        assertEquals(expected, result)
    }

    @Test
    fun `spy class returning complex value class not boxed due to suspend function`() {
        val f = spy<DummyService>()
        val result = runBlocking { f.returnComplexValueClassSuspendInlined() }

        assertEquals(ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3")), result)
    }

    @Test
    fun `mock class returning complex value class not boxed due to suspend function`() {
        val f = mock<DummyService>()
        coEvery { f.returnComplexValueClassSuspendInlined() } returns ComplexValue(UUID.fromString("bca61f8d-ba4d-475f-8dc6-08b943836998"))
        val result = runBlocking { f.returnComplexValueClassSuspendInlined() }

        assertEquals(ComplexValue(UUID.fromString("bca61f8d-ba4d-475f-8dc6-08b943836998")), result)
    }

    @Test
    fun `value class argument with a validating init matches any()`() {
        val mock = mock<UserOfMyToken>()
        assertDoesNotThrow {
            every { mock.useMyToken(any()) } returns Unit
        }

        mock.useMyToken(MyToken("MY_PREFIX::actualCallValue"))

        verify { mock.useMyToken(MyToken("MY_PREFIX::actualCallValue")) }
    }

    companion object {
        @JvmInline
        value class MyToken(
            val raw: String,
        ) {
            init {
                require(raw.startsWith("MY_PREFIX::")) { "Token must start with MY_PREFIX::" }
            }
        }

        interface UserOfMyToken {
            fun useMyToken(myToken: MyToken)
        }

        @JvmInline
        value class ValueTestClass(
            val int: Int,
        )

        interface ClassProvider {
            fun provideTestClass(): ValueTestClass?
        }

        @JvmInline
        value class NonPrimitiveValueTestClass(
            val string: String,
        )

        interface NonPrimitiveClassProvider {
            fun provideTestClass(): NonPrimitiveValueTestClass?
        }

        @JvmInline
        value class ValueClassId(
            val id: String,
        )

        interface ValueClassWithOptionalReturn {
            fun something(data: ValueClassId): ValueClassId?
        }

        @JvmInline
        value class BooleanValue(
            val value: Boolean,
        )

        interface NullableBooleanValueConsumer {
            fun consume(
                first: BooleanValue?,
                second: BooleanValue?,
            )
        }

        @JvmInline
        value class DummyValue(
            val value: Int,
        ) {
            val text: String get() = value.toString()
        }

        @JvmInline
        value class ComplexValue(
            val value: UUID,
        ) {
            val text: String get() = value.toString()
        }

        @JvmInline
        value class DummyValueWrapper(
            val value: DummyValue,
        )

        @JvmInline
        value class DummyComplexValueWrapper(
            val value: ComplexValue,
        )

        @Suppress("UNUSED_PARAMETER")
        open class DummyService {
            open val valueClassField = DummyValue(0)
            open val nullableValueClassField: DummyValue? = null
            open val nullableComplexValueClassField: ComplexValue? = null
            open val nullableNestedValueClassField: DummyValueWrapper? = null
            open val nullableNestedComplexValueClassField: DummyComplexValueWrapper? = null

            open fun argWrapperReturnWrapper(wrapper: DummyValueWrapper): DummyValueWrapper = DummyValueWrapper(DummyValue(0))

            open fun argWrapperReturnValueClass(wrapper: DummyValueWrapper): DummyValue = DummyValue(0)

            open fun argValueClassReturnWrapper(valueClass: DummyValue): DummyValueWrapper = DummyValueWrapper(valueClass)

            open fun argGenericValueClassReturnResultWrapped(valueClass: ComplexValue): Result<ComplexValue> = Result.success(valueClass)

            open fun argValueClassReturnValueClass(valueClass: DummyValue): DummyValue = DummyValue(0)

            open fun argAnyReturnString(value: Any): String = value.toString()

            open fun argNullableWrapperReturnString(wrapper: DummyValueWrapper?): String = wrapper.toString()

            open fun argComplexValueClassReturnComplexValueClass(complexValue: ComplexValue): ComplexValue =
                ComplexValue(UUID.fromString("7dea337b-ce0b-4e25-9788-79e708aadc33"))

            open fun returnValueClass(): DummyValue = DummyValue(0)

            open fun returnValueClassNotInlined(): Any = DummyValue(0)

            open suspend fun returnValueClassSuspendNotInlined(): DummyValue = DummyValue(0)

            open suspend fun returnComplexValueClassSuspendInlined(): ComplexValue =
                ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3"))

            open suspend fun returnNestedValueClassSuspendNotInlined(): DummyValueWrapper = DummyValueWrapper(DummyValue(0))

            open suspend fun returnNestedComplexValueClassSuspendNotInlined(): DummyComplexValueWrapper =
                DummyComplexValueWrapper(ComplexValue(UUID.fromString("c5744ead-302f-4e29-9f82-d10eb2a85ea3")))

            open fun argNoneReturnsUInt(): UInt = 123u

            open fun nullableValueClass(): DummyValue? = null

            open fun nullableComplexValueClass(): ComplexValue? = null

            open fun nullableNestedValueClass(): DummyValueWrapper? = null

            open fun nullableNestedComplexValueClass(): DummyComplexValueWrapper? = null
        }
    }
}
