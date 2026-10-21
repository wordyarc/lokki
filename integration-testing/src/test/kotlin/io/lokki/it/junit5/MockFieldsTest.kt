package io.lokki.it.junit5

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.checkUnnecessaryStub
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.isMock
import io.lokki.it.Car
import io.lokki.it.Garage
import io.lokki.it.Workshop
import io.lokki.junit5.LokkiExtension
import io.lokki.verify
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExtendWith(LokkiExtension::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MockFieldsTest {
    @Mock
    lateinit var car: Car

    @RelaxedMock
    lateinit var relaxedCar: Car

    @Spy
    var workshop = Workshop("lokki", capacity = 1)

    @InjectMocks
    lateinit var garage: Garage

    @Test
    @Order(1)
    fun mocksOfFieldsBelongToTest() {
        first = car
        every { car.drive(1) } returns "driven"
        assertEquals("driven", Garage(car).leave())

        assertFailsWith<AssertionError> { confirmVerified() }
        verify { car.drive(1) }
        confirmVerified()
    }

    @Test
    @Order(2)
    fun everyTestGetsNewMocks() {
        assertNotSame(first, car)
        assertEquals("", relaxedCar.drive(1))
    }

    @Test
    @Order(3)
    fun spyFieldIsCreatedWithTheInstance() {
        every { workshop.service(any()) } returns listOf("stubbed")
        assertEquals(listOf("stubbed"), workshop.service(listOf(car)))
        assertEquals(listOf("open"), workshop.log())
    }

    @Test
    @Order(4)
    fun initAgainKeepsMocks() {
        val car = car
        MockAnnotations.init(this, relaxed = true)
        assertSame(car, this.car)
    }

    @Test
    @Order(5)
    fun injectedObjectGetsMocksOfTest() {
        every { car.drive(1) } returns "injected"
        assertEquals("injected", garage.leave())
        assertEquals("Garage($car)", garage.toString())
    }

    @Nested
    inner class NestedClass {
        @Mock
        lateinit var nestedCar: Car

        @Test
        fun initializesNestedAndOuterInstances() {
            assertTrue(isMock(nestedCar) && isMock(car))
        }
    }

    private companion object {
        var first: Car? = null
    }
}

@ExtendWith(LokkiExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class PerClassMockFieldsTest {
    @Mock
    lateinit var car: Car

    @BeforeAll
    fun stubForAllTests() {
        every { car.plate } returns "class"
    }

    @Test
    @Order(1)
    fun stubsOfTestAreRolledBack() {
        every { car.plate } returns "test"
        assertEquals("test", car.plate)
    }

    @Test
    @Order(2)
    fun stubsOfBeforeAllSurviveTests() {
        assertEquals("class", Garage(car).plate())
        checkUnnecessaryStub()
    }
}
