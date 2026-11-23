package io.lokki.it.conformance

import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import java.util.Optional
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OptionalReturnOnRelaxedMockTest {
    open class ReturningOptionals {
        open fun getOptional(): Optional<String> = Optional.empty()

        open fun getOptionalInt(): OptionalInt = OptionalInt.empty()

        open fun getOptionalLong(): OptionalLong = OptionalLong.empty()

        open fun getOptionalDouble(): OptionalDouble = OptionalDouble.empty()
    }

    @RelaxedMock
    private lateinit var returningOptionals: ReturningOptionals

    @BeforeTest
    fun setup() {
        MockAnnotations.init(this, relaxUnitFun = true)
    }

    @Test
    fun getOptional() {
        val optional = returningOptionals.getOptional()
        assertFalse(optional.isPresent)
        assertEquals(Optional.empty<String>(), optional)
    }

    @Test
    fun getOptionalInt() {
        val optionalInt = returningOptionals.getOptionalInt()
        assertFalse(optionalInt.isPresent)
        assertEquals(OptionalInt.empty(), optionalInt)
    }

    @Test
    fun getOptionalLong() {
        val optionalLong = returningOptionals.getOptionalLong()
        assertFalse(optionalLong.isPresent)
        assertEquals(OptionalLong.empty(), optionalLong)
    }

    @Test
    fun getOptionalDouble() {
        val optionalDouble = returningOptionals.getOptionalDouble()
        assertFalse(optionalDouble.isPresent)
        assertEquals(OptionalDouble.empty(), optionalDouble)
    }
}
