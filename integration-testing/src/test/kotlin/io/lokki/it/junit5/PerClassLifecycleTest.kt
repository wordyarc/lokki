package io.lokki.it.junit5

import io.lokki.checkUnnecessaryStub
import io.lokki.every
import io.lokki.it.Car
import io.lokki.junit5.LokkiExtension
import io.lokki.mock
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals

@ExtendWith(LokkiExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PerClassLifecycleTest {
    private val car = mock<Car>()

    @BeforeAll
    fun stubForAllTests() {
        every { car.drive(any()) } returnsMany listOf("a", "b")
        every { car.plate } returns "class"
    }

    @Test
    @Order(1)
    fun testStubsOfClassMockAreRolledBack() {
        assertEquals(listOf("a", "b"), List(2) { car.drive(1) })
        every { car.plate } returns "test"
        assertEquals("test", car.plate)
    }

    @Test
    @Order(2)
    fun stubsOfBeforeAllSurviveTests() {
        assertEquals("a", car.drive(1))
        assertEquals("class", car.plate)
        checkUnnecessaryStub()
    }
}
