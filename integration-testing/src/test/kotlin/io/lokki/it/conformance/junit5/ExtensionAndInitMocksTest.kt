package io.lokki.it.conformance.junit5

import io.lokki.AdditionalInterface
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.junit5.LokkiExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExtendWith(LokkiExtension::class)
class ExtensionAndInitMocksTest {
    open class Car

    @Mock
    private lateinit var car: Car

    @RelaxedMock
    private lateinit var relaxedCar: Car

    @Spy
    private var carSpy = Car()

    @Mock
    @AdditionalInterface(Runnable::class)
    private lateinit var runnableCar: Car

    @RelaxedMock
    @AdditionalInterface(Runnable::class)
    private lateinit var runnableRelaxedCar: Car

    @Spy
    @AdditionalInterface(Runnable::class)
    private var runnableCarSpy = Car()

    @Test
    fun initMocksAfterExtension() {
        val carSaved = car
        val relaxedCarSaved = relaxedCar
        val carSpySaved = carSpy

        MockAnnotations.init(this)

        assertSame(carSaved, car)
        assertSame(relaxedCarSaved, relaxedCar)
        assertSame(carSpySaved, carSpy)

        MockAnnotations.init(this)

        assertSame(carSaved, car)
        assertSame(relaxedCarSaved, relaxedCar)
        assertSame(carSpySaved, carSpy)
    }

    @Suppress("USELESS_CAST")
    @Test
    fun allRunnable() {
        assertTrue { runnableCar as Any is Runnable }
        assertTrue { runnableRelaxedCar as Any is Runnable }
        assertTrue { runnableCarSpy as Any is Runnable }
    }
}
