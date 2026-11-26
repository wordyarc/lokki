package io.lokki

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MockAnnotationsWithoutPluginTest {
    interface Car

    open class Fields {
        @Mock
        lateinit var car: Car
    }

    class Inherited : Fields()

    class Relaxed {
        @RelaxedMock
        private lateinit var car: Car
    }

    class Injected {
        @InjectMocks
        lateinit var garage: Any
    }

    class Overridden {
        @OverrideMocks
        lateinit var garage: Any
    }

    class ConstructedSpy {
        @Spy
        val car: Car = object : Car {}
    }

    class Parameter(@Mock val car: Car)

    class ParameterAndField(@RelaxedMock val car: Car) {
        @Mock
        lateinit var spare: Car
    }

    class Plain {
        lateinit var car: Car
    }

    @Test
    fun testInitThrowsForMockPropertiesCompiledWithoutCompilerPlugin() {
        val fields = Fields::class.java.name
        val owners = listOf(
            Fields() to fields,
            Inherited() to fields,
            Relaxed() to Relaxed::class.java.name,
            Injected() to Injected::class.java.name,
            Overridden() to Overridden::class.java.name,
        )

        for ((target, owner) in owners) {
            val exception = assertFailsWith<LokkiException> { MockAnnotations.init(target) }

            assertEquals(
                "lokki compiler plugin is not applied to the compilation of $owner, so its mock properties are " +
                    "not initialized: with Gradle, name the compilation in lokki { testCompilations(...) }, " +
                    "or set applyToMain = true for main code",
                exception.message,
            )
        }
    }

    @Test
    fun testInitSkipsPropertiesThatNeedNoInitializer() {
        val car = object : Car {}

        MockAnnotations.init(ConstructedSpy(), Parameter(car), ParameterAndField(car), Plain(), Any(), "text")
    }
}
