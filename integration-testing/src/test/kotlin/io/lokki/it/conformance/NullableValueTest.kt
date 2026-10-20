package io.lokki.it.conformance

import io.lokki.mock
import io.lokki.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NullableValueTest {
    @Test
    fun testRelaxedMockReturnsNull() {
        class Bar

        @Suppress("RedundantNullableReturnType", "RedundantSuspendModifier")
        open class Foo {
            open val property: Bar? = Bar()
            open val isEnabled: Boolean? = false

            open fun getSomething(): Bar? = Bar()

            open suspend fun getOtherThing(): Bar? = Bar()
        }

        val mock = mock<Foo>(relaxed = true)
        assertEquals(null, mock.property)
        assertEquals(null, mock.isEnabled)
        assertEquals(null, mock.getSomething())
        assertEquals(null, runBlocking { mock.getOtherThing() })
    }

    @Test
    fun withNullableArgMatchesAndExecutesCaptureBlockWhenArgumentIsNull() {
        open class MockedClass {
            open fun printNullableString(s: String?) {
                println(s)
            }
        }

        class TestedClass(
            private val mockedClass: MockedClass,
        ) {
            fun testNullString() {
                val testString: String? = null
                mockedClass.printNullableString(testString)
            }
        }

        val mock = mock<MockedClass>(relaxed = true)
        val testedClass = TestedClass(mock)

        testedClass.testNullString()

        verify(exactly = 1) {
            mock.printNullableString(withNullableArg { println(it) })
        }
    }
}
