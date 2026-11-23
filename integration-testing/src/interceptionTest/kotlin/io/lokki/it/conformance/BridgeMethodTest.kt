package io.lokki.it.conformance

import io.lokki.every
import io.lokki.it.conformance.bridge.FinalIntSource
import io.lokki.it.conformance.bridge.IntSource
import io.lokki.it.conformance.bridge.NumberSource
import io.lokki.it.conformance.bridge.Sink
import io.lokki.it.conformance.bridge.Source
import io.lokki.it.conformance.bridge.Square
import io.lokki.it.conformance.bridge.StringSink
import io.lokki.it.conformance.bridge.StringSource
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BridgeMethodTest {
    @Test
    fun stubbedCovariantFinalPropertyIsReturnedThroughSupertype() {
        val mock = mock<FinalIntSource>()

        every { mock.number } returns 800

        assertEquals(800, mock.number)
        assertEquals(800, (mock as IntSource).number)
        assertEquals(800, (mock as NumberSource).number)
    }

    @Test
    fun callThroughSupertypeIsVerifiedAgainstTheOverridingProperty() {
        val mock = mock<FinalIntSource>()
        every { mock.number } returns 800

        (mock as NumberSource).number

        verify(exactly = 1) { mock.number }
    }

    @Test
    fun stubRecordedThroughSupertypeAnswersTheOverridingProperty() {
        val mock = mock<FinalIntSource>()

        every { (mock as NumberSource).number } returns 7

        assertEquals(7, mock.number)
        assertEquals(7, (mock as NumberSource).number)
    }

    @Test
    fun stubbedFinalPropertyOfGenericSupertypeIsReturnedThroughSupertype() {
        val mock = mock<StringSource>()

        every { mock.value } returns "stubbed"

        assertEquals("stubbed", (mock as Source<*>).value)
    }

    @Test
    fun spyCallThroughSupertypeIsRecordedOnce() {
        val spy = spy(FinalIntSource(5))

        assertEquals(5, (spy as NumberSource).number)

        verify(exactly = 1) { spy.number }
    }

    @Test
    fun spyCallThroughBridgeWithPrimitiveParameterIsRecordedOnce() {
        val spy = spy(Square())

        assertEquals(9, (spy as (Int) -> Int)(3))

        verify(exactly = 1) { spy(3) }
    }

    @Test
    fun spyOfFunctionReferenceIsRecordedOnce() {
        fun square(value: Int) = value * value
        val spy = spy(::square)

        assertEquals(9, spy(3))

        verify(exactly = 1) { spy(3) }
    }

    @Test
    fun bridgeKeepsItsOwnBehaviourForArgumentsTheBridgedMethodCannotTake() {
        val mock = mock<StringSink>()
        every { mock.accept("stubbed") } returns true

        @Suppress("UNCHECKED_CAST")
        val untyped = mock as Sink<Any>

        assertTrue(untyped.accept("stubbed"))
        assertFailsWith<ClassCastException> { untyped.accept(1) }
    }
}
