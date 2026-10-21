package io.lokki.it.junit5

import io.lokki.checkUnnecessaryStub
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.it.Car
import io.lokki.it.Garage
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import io.lokki.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

@ExtendWith(LokkiExtension::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class LokkiExtensionTest {
    private val car = mock<Car> { every { plate } returns "property" }

    private lateinit var beforeEachThread: Thread

    @BeforeEach
    fun rememberThread() {
        beforeEachThread = Thread.currentThread()
    }

    @Test
    @Order(1)
    fun mocksOfPropertyInitializersBelongToTest() {
        assertFailsWith<AssertionError> { checkUnnecessaryStub() }
        assertEquals("property", Garage(car).plate())
        checkUnnecessaryStub()
    }

    @Test
    @Order(2)
    fun testStubsOnStaticMockAreRolledBack() {
        every { shared.drive(1) } returns "test"
        assertEquals("test", Garage(shared).leave())
    }

    @Test
    @Order(3)
    fun staticStubsSurviveTests() {
        assertEquals("static", Garage(shared).leave())
    }

    @Test
    @Order(4)
    fun answerPositionsOfBeforeAllStubsAreRestored() {
        assertEquals(listOf("a", "b", "b"), List(3) { classMock.drive(1) })
    }

    @Test
    @Order(5)
    fun answerPositionsOfBeforeAllStubsAreRestoredForNextTest() {
        assertEquals("a", classMock.drive(1))
    }

    @RepeatedTest(2)
    @Order(6)
    fun repetitionsHaveOwnScopes() {
        assertEquals("static", shared.drive(2))
        verify { shared.drive(2) }
        every { shared.drive(2) } returns "repetition"
        confirmVerified()
    }

    @TestFactory
    @Order(7)
    fun dynamicTestsHaveOwnScopes(): List<DynamicTest> {
        confirmVerified()
        return List(2) { index ->
            dynamicTest("dynamic $index") {
                assertEquals("static", shared.drive(3))
                verify { shared.drive(3) }
                every { shared.drive(3) } returns "dynamic"
                confirmVerified()
            }
        }
    }

    @Test
    @Order(8)
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun scopeIsCurrentInTimeoutThread() {
        assertNotSame(beforeEachThread, Thread.currentThread())
        val other = mock<Car>()
        every { other.drive(any()) } returns "driven"
        Garage(other).leave()

        assertFailsWith<AssertionError> { confirmVerified() }
        verify { other.drive(1) }
        confirmVerified()
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class NestedClass {
        private val nested = mock<Car>()

        @BeforeAll
        fun stubForNestedClass() {
            every { nested.plate } returns "nested class"
        }

        @Test
        @Order(1)
        fun seesStubsOfOuterAndNestedClass() {
            assertEquals("a", classMock.drive(1))
            assertEquals("nested class", nested.plate)
            every { nested.plate } returns "nested test"
        }

        @Test
        @Order(2)
        fun stubsOfNestedTestAreRolledBack() {
            assertEquals("nested class", nested.plate)
        }
    }

    private companion object {
        val shared = mock<Car> { every { drive(any()) } returns "static" }

        lateinit var classMock: Car

        @BeforeAll
        @JvmStatic
        fun stubForClass() {
            classMock = mock { every { drive(any()) } returnsMany listOf("a", "b") }
        }
    }
}
