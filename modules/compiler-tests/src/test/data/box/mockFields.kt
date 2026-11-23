// CHECK_BYTECODE_TEXT

package app

import io.lokki.AdditionalInterface
import io.lokki.LokkiException
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.RelaxedMock
import io.lokki.Spy
import io.lokki.every
import io.lokki.isMock
import io.lokki.verify

interface Car {
    fun drive(speed: Int): String
    fun honk()
}

open class Engine(private val power: Int = 100) {
    open fun power(): Int = power
    open fun start(): String = "started at ${power()}"
}

interface Named {
    fun name(): String
}

open class Box<T>(private val item: T) {
    open fun get(): T = item
}

class Fixture {
    @Mock
    lateinit var car: Car

    @Mock(name = "named car")
    lateinit var namedCar: Car

    @Mock(relaxed = true)
    lateinit var relaxedCar: Car

    @Mock(relaxUnitFun = true)
    lateinit var unitCar: Car

    @RelaxedMock
    lateinit var otherRelaxedCar: Car

    @Mock
    var nullableCar: Car? = null

    @Mock
    @AdditionalInterface(Named::class)
    lateinit var namedEngine: Engine

    @Spy
    var engine = Engine(7)

    @Spy
    val defaultEngine = Engine()

    @Spy
    lateinit var lateEngine: Engine

    @Spy
    var existingEngine: Engine = existing

    @Spy
    @AdditionalInterface(Named::class)
    var namedSpy = Engine(3)

    @Spy
    var box = Box("item")

    @Spy
    lateinit var spiedCar: Car

    var notAnnotated: Car? = null

    companion object {
        val existing = Engine(5)
    }
}

class Generic<T : Any>(item: T) {
    @Mock
    lateinit var box: Box<T>

    @Spy
    var spy = Box(item)
}

private fun answer(block: () -> Any?): String =
    try {
        block().toString()
    } catch (e: LokkiException) {
        e.message!!.lineSequence().first().replace(Regex("#\\d+"), "#")
    }

fun box(): String {
    val fixture = Fixture()
    if (listOf(fixture.engine, fixture.defaultEngine, fixture.namedSpy).any { !isMock(it, regular = false, spy = true) }) {
        return "Fail: constructed spies are created with the object"
    }
    if (fixture.nullableCar != null || fixture.notAnnotated != null) return "Fail: before init"
    MockAnnotations.init(fixture)

    if (answer { fixture.car.drive(1) } != "no answer found for Car(car#).drive(1) among the configured answers: ()") {
        return "Fail: strict mock named after the property: ${answer { fixture.car.drive(1) }}"
    }
    if (!fixture.namedCar.toString().startsWith("Car(named car#")) return "Fail: name ${fixture.namedCar}"
    if (fixture.relaxedCar.drive(1) != "") return "Fail: relaxed"
    fixture.unitCar.honk()
    if (answer { fixture.unitCar.drive(1) }.startsWith("no answer found").not()) return "Fail: relaxUnitFun"
    if (fixture.otherRelaxedCar.drive(1) != "") return "Fail: @RelaxedMock"
    if (fixture.nullableCar == null || !isMock(fixture.nullableCar!!)) return "Fail: nullable var"
    if (fixture.namedEngine !is Named) return "Fail: additional interface of a mock"
    if (fixture.notAnnotated != null) return "Fail: not annotated"

    if (fixture.engine.start() != "started at 7") return "Fail: constructed spy ${fixture.engine.start()}"
    every { fixture.engine.power() } returns 70
    if (fixture.engine.start() != "started at 70") return "Fail: constructed spy calls itself"
    if (fixture.defaultEngine.power() != 100) return "Fail: val spy"
    if (fixture.lateEngine.power() != 100 || !isMock(fixture.lateEngine, regular = false, spy = true)) return "Fail: lateinit spy"
    val existing = fixture.existingEngine
    if (existing === Fixture.existing || !isMock(existing, regular = false, spy = true)) return "Fail: delegating spy"
    every { existing.power() } returns 50
    if (existing.start() != "started at 5") return "Fail: delegating spy forwards to the instance"
    if (existing.power() != 50) return "Fail: delegating spy stub"
    if (fixture.namedSpy !is Named || fixture.namedSpy.power() != 3) return "Fail: additional interface of a spy"
    if (fixture.box.get() != "item") return "Fail: generic spy"
    fixture.spiedCar.honk()
    if (answer { fixture.spiedCar.drive(1) }.startsWith("no original implementation").not()) {
        return "Fail: spy of an interface ${answer { fixture.spiedCar.drive(1) }}"
    }
    verify { fixture.spiedCar.honk() }

    val car = fixture.car
    val engine = fixture.engine
    MockAnnotations.init(fixture, relaxed = true)
    if (fixture.car !== car || fixture.engine !== engine) return "Fail: init replaced initialized properties"

    val relaxed = Fixture()
    MockAnnotations.init(relaxed, relaxed = true)
    if (relaxed.car.drive(1) != "") return "Fail: relaxed by init"
    val unit = Fixture()
    MockAnnotations.init(unit, relaxUnitFun = true)
    unit.car.honk()

    val generic = Generic(42)
    MockAnnotations.init(generic)
    every { generic.box.get() } returns 1
    if (generic.box.get() != 1 || generic.spy.get() != 42) return "Fail: generic class"

    MockAnnotations.init(Any(), "not a mock owner")
    return "OK"
}

// 2 public final synthetic lokki\$initMockFields\(I\)V
// 2 implements io/lokki/internal/MockFieldsOwner
