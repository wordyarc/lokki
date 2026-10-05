package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import java.io.InputStream
import java.lang.reflect.Modifier
import java.util.Random

open class Engine(val power: Int) {
    val serial: String = "S-1"

    init {
        error("the constructor must not run")
    }

    open fun start(): String = "started"

    open val name: String
        get() = "engine"

    fun describe(): String = "$name $power"

    final override fun toString(): String = "Engine($power)"
}

abstract class Pump {
    abstract fun pressure(): Int

    protected abstract fun prime(): Boolean

    fun run(): String = if (prime()) "running at ${pressure()}" else "dry"
}

abstract class Valve(val size: Int, unit: String = "mm") {
    abstract fun open(percent: Int): Boolean
}

open class Gauge private constructor(val scale: Int) {
    protected constructor() : this(10)

    open fun read(): Int = scale
}

@JvmInline
value class Code(val value: Int)

open class Lock(val code: Code) {
    open fun unlock(code: Code): Boolean = code == this.code
}

class Outer {
    open inner class Inner {
        open fun id(): Int = 1
    }
}

abstract class Box<T> {
    abstract fun get(): T

    open fun put(value: T): Boolean = false
}

open class Names : Box<String>() {
    override fun get(): String = "name"
}

fun noAnswer(block: () -> Unit): String? =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    val engine = mock<Engine>()
    val mockClass = engine.javaClass

    if (mockClass.name != "sample.Engine\$LokkiMock\$dabe4326\$MockClassKt") return "Fail name: ${mockClass.name}"
    if (mockClass.superclass != Engine::class.java) return "Fail superclass: ${mockClass.superclass}"
    val interfaces = mockClass.interfaces.map { it.name }
    if (interfaces != listOf("io.lokki.internal.GeneratedMock")) return "Fail interfaces: $interfaces"
    if (Modifier.isPublic(mockClass.modifiers) || !Modifier.isFinal(mockClass.modifiers)) return "Fail modifiers"
    if (!mockClass.declaredConstructors.all { Modifier.isPrivate(it.modifiers) }) return "Fail: public constructor"
    if (mock<Engine>().javaClass != mockClass) return "Fail: second mock in the file has another class"

    if (engine.power != 0 || (engine.serial as String?) != null) return "Fail: the constructor of Engine ran"
    val strict = noAnswer { engine.start() }.orEmpty()
    if (!Regex("""no answer found for Engine\(#\d+\)\.start\(\) among the configured answers: \(\)""").matches(strict)) {
        return "Fail strict: $strict"
    }
    every { engine.start() } returns "stubbed"
    every { engine.name } returns "mocked"
    if (engine.start() != "stubbed") return "Fail: stub of an open member"
    if (engine.describe() != "mocked 0") return "Fail: final member calling a mocked one: ${engine.describe()}"
    if (engine.toString() != "Engine(0)") return "Fail: final toString is not the original: $engine"

    val pump = mock<Pump>()
    every { pump.pressure() } returns 5
    if (pump.pressure() != 5) return "Fail: abstract member"
    if (noAnswer { pump.run() }?.startsWith("no answer found for Pump(#") != true) return "Fail: protected member"

    val valve = mock<Valve>()
    every { valve.open(more(10)) } returns true
    if (!valve.open(50) || valve.size != 0) return "Fail: class with constructor parameters"

    val gauge = mock<Gauge>()
    every { gauge.read() } returns 3
    if (gauge.read() != 3) return "Fail: class with a protected constructor"

    val lock = mock<Lock>()
    every { lock.unlock(Code(1)) } returns true
    if (!lock.unlock(Code(1))) return "Fail: class with a value class in the constructor"

    val inner = mock<Outer.Inner>()
    every { inner.id() } returns 2
    if (inner.id() != 2) return "Fail: inner class"

    val names = mock<Names>()
    every { names.get() } returns "mocked"
    every { names.put(any()) } returns true
    val box: Box<String> = names
    if (box.get() != "mocked" || !box.put("x")) return "Fail: members of a generic superclass"

    val stream = mock<InputStream>()
    every { stream.read() } returns 7
    if (stream.read() != 7) return "Fail: abstract Java class"

    val random = mock<Random>()
    every { random.nextInt() } returns 4
    if (random.nextInt() != 4) return "Fail: Java class"

    val state = mockClass.getDeclaredField("lokki\$state")
    val init = mockClass.getDeclaredMethod("lokki\$init", Class.forName("io.lokki.internal.MockSpec"))
    if (!state.isSynthetic || Modifier.isFinal(state.modifiers)) return "Fail: state field is final or not synthetic"
    if (!init.isSynthetic || !Modifier.isPublic(init.modifiers)) return "Fail: lokki\$init is not synthetic or public"

    return "OK"
}
