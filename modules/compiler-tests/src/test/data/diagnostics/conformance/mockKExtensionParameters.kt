// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

package sample

import io.lokki.AdditionalInterface
import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.every

open class Car {
    open fun recordTelemetry(speed: Int): String = "failure"
}

open class Foo

class MockKExtensionTest {
    fun injectsValidMockInMethods(
        <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> car: Car,
    ) {
        every { car.recordTelemetry(speed = more(50)) } returns "recorded"
    }

    fun injectsValidRelaxedMockInMethods(
        <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@RelaxedMock<!> car: Car,
    ) {
        car.recordTelemetry(1)
    }
}

class MockKExtensionOnNestedClassTest {
    inner class InjectMockInNestedTest(
        <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> private val foo: Foo,
        <!LOKKI_UNSUPPORTED_MOCK_PARAMETER!>@Mock<!> @AdditionalInterface(Runnable::class) private val bar: Foo,
    )
}
