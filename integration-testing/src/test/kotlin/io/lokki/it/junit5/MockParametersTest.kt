package io.lokki.it.junit5

import io.lokki.AdditionalInterface
import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.isMock
import io.lokki.junit5.LokkiExtension
import io.lokki.verify
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

interface Engine {
    fun power(): Int
}

open class Gearbox {
    open fun ratio(): Int = 3
}

class Car(val engine: Engine) {
    var gearbox: Gearbox? = null
}

@ExtendWith(LokkiExtension::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MockParametersTest(@Mock private val inConstructor: Engine) {
    @Test
    @Order(1)
    fun mockInMethod(@Mock engine: Engine) {
        previous = engine
        every { engine.power() } returns 7
        assertEquals(7, engine.power())
        assertTrue(isMock(inConstructor))
        assertNotSame(inConstructor, engine)
    }

    @Test
    @Order(2)
    fun injectedObjectGetsMockOfThisTest(@InjectMocks car: Car, @Mock engine: Engine, @Spy gearbox: Gearbox) {
        assertSame(engine, car.engine)
        assertSame(gearbox, car.gearbox)
        assertNotSame(previous, engine)
        assertEquals(3, gearbox.ratio())
        verify { gearbox.ratio() }
    }

    @Test
    fun relaxedAndAdditionalInterface(
        @RelaxedMock engine: Engine,
        @Mock(relaxUnitFun = true) @AdditionalInterface(Runnable::class) other: Engine,
    ) {
        assertEquals(0, engine.power())
        (other as Runnable).run()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun mixedWithParameterizedArguments(value: Int, @Mock engine: Engine) {
        every { engine.power() } returns value
        assertEquals(value, engine.power())
    }

    @RepeatedTest(2)
    fun newMockForEveryRepetition(@Mock engine: Engine) {
        assertNotSame(repeated, engine)
        repeated = engine
    }

    @Nested
    inner class NestedClass(@Mock private val nested: Engine) {
        @Test
        fun constructorOfInnerClass(@Mock engine: Engine) {
            assertTrue(isMock(nested))
            assertNotSame(nested, engine)
        }
    }

    private companion object {
        var previous: Engine? = null
        var repeated: Engine? = null
    }
}
