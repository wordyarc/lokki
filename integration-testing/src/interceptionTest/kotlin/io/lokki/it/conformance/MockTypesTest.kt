package io.lokki.it.conformance

import io.lokki.isMock
import io.lokki.it.conformance.types.TestCls
import io.lokki.it.conformance.types.TestObject
import io.lokki.it.conformance.types.TestSub
import io.lokki.it.conformance.types.TestSubChild
import io.lokki.it.conformance.types.newTestCls
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.mockObject
import io.lokki.mockStatic
import io.lokki.spy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockTypesTest {
    enum class MockType {
        REGULAR,
        SPY,
        OBJECT,
        STATIC,
        CONSTRUCTOR,
    }

    @Test
    fun regularMock() {
        assertOnlyOfType(mock<TestCls>(), MockType.REGULAR)
    }

    @Test
    fun spyMock() {
        assertOnlyOfType(spy<TestCls>(), MockType.SPY)
    }

    @Test
    fun delegatingSpyMock() {
        val existing = TestSub()
        assertOnlyOfType(spy(existing), MockType.SPY)
    }

    @Test
    fun objectMock() {
        assertNotMock(TestObject)
        lokkiScope {
            mockObject(TestObject) {
                assertOnlyOfType(TestObject, MockType.OBJECT)
            }
            assertNotMock(TestObject)
        }
    }

    @Test
    fun staticMock() {
        val facade = Class.forName("io.lokki.it.conformance.types.FixturesKt")
        assertNotMock(facade)
        lokkiScope {
            mockStatic("io.lokki.it.conformance.types.FixturesKt") {
                assertOnlyOfType(facade, MockType.STATIC)
                assertOnlyOfType(facade.kotlin, MockType.STATIC)
                assertNotMock(TestCls::class)
            }
            assertNotMock(facade)
        }
    }

    @Test
    fun constructorMock() {
        assertNotMock(TestCls::class)
        lokkiScope {
            mockConstructor(TestCls::class) {
                assertOnlyOfType(TestCls::class, MockType.CONSTRUCTOR)
                assertOnlyOfType(TestCls::class.java, MockType.CONSTRUCTOR)
                assertNotMock(Any::class)
                assertOnlyOfType(newTestCls(), MockType.REGULAR)
            }
            assertNotMock(TestCls::class)
        }
    }

    @Test
    fun constructorMockIsOnlyForTheExactClass() {
        lokkiScope {
            mockConstructor(TestSub::class) {
                assertOnlyOfType(TestSub::class, MockType.CONSTRUCTOR)
                assertNotMock(TestSubChild::class)
            }
        }
    }

    private fun assertNotMock(mock: Any) {
        for (type in MockType.entries) assertFalse(isOfMockType(mock, type), "$mock should not be of type $type")
    }

    private fun assertOnlyOfType(
        mock: Any,
        singleType: MockType,
    ) {
        for (type in MockType.entries) {
            if (singleType == type) {
                assertTrue(isOfMockType(mock, type), "mock is not of type $singleType")
            } else {
                assertFalse(isOfMockType(mock, type), "mock should be of type $singleType, but it is as well $type")
            }
        }
    }

    private fun isOfMockType(
        mock: Any,
        type: MockType,
    ) = when (type) {
        MockType.REGULAR -> isMock(mock)
        MockType.SPY -> isMock(mock, regular = false, spy = true)
        MockType.OBJECT -> isMock(mock, regular = false, objectMock = true)
        MockType.STATIC -> isMock(mock, regular = false, staticMock = true)
        MockType.CONSTRUCTOR -> isMock(mock, regular = false, constructorMock = true)
    }
}
