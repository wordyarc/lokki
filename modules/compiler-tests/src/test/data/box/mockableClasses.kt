// MODULE: main
// MOCKABLE: sample.engine.** sample.Wheel sample.Mockable
// FILE: engine.kt
package sample.engine

class Engine(val power: Int, @JvmField val code: String = "code") {
    var mode: String = "eco"

    @JvmField
    val serial: String = "serial"

    var revision: Int = 0
        private set

    fun start(): String = "started"

    final fun id(): Int = 1

    inline fun twice(block: () -> Int): Int = block() + block()

    tailrec fun countdown(n: Int): Int = if (n == 0) 0 else countdown(n - 1)

    class Part {
        fun name(): String = "part"
    }

    companion object {
        fun create(): Engine = Engine(1)
    }
}

abstract class Starter {
    fun kick(): String = "kick"
}

data class Spec(val size: Int) {
    fun describe(): String = "spec $size"
}

final class Locked {
    fun open(): String = "locked"
}

@JvmInline
value class Rpm(val value: Int)

object Registry {
    fun count(): Int = 1
}

enum class Mode { ECO }

// FILE: piston.kt
package sample.engine.parts

class Piston {
    fun move(): String = "move"
}

// FILE: gear.kt
package sample.engineering

class Gear {
    fun turn(): String = "turn"
}

// FILE: sample.kt
package sample

annotation class Mockable

class Wheel {
    fun spin(): String = "spin"
}

class WheelCover {
    fun fit(): String = "fit"
}

@Mockable
class Door {
    fun open(): String = "open"
}

class Window {
    fun open(): String = "open"
}

// MODULE: test()(main)
// FILE: test.kt
package sample.test

import io.lokki.every
import io.lokki.mock
import java.lang.reflect.Modifier
import sample.Door
import sample.Wheel
import sample.WheelCover
import sample.Window
import sample.engine.Engine
import sample.engine.Locked
import sample.engine.Mode
import sample.engine.Registry
import sample.engine.Rpm
import sample.engine.Spec
import sample.engine.Starter
import sample.engine.parts.Piston
import sample.engineering.Gear

private fun Class<*>.isFinal(): Boolean = Modifier.isFinal(modifiers)

private fun Class<*>.isFinal(method: String, vararg parameters: Class<*>): Boolean =
    Modifier.isFinal(getDeclaredMethod(method, *parameters).modifiers)

fun box(): String {
    val engine = mock<Engine>()
    every { engine.start() } returns "mocked"
    every { engine.power } returns 7
    every { engine.mode } returns "sport"
    if (engine.start() != "mocked") return "Fail: function of a class in a mockable package"
    if (engine.power != 7) return "Fail: constructor property"
    if (engine.mode != "sport") return "Fail: property"

    val part = mock<Engine.Part>()
    every { part.name() } returns "mocked"
    if (part.name() != "mocked") return "Fail: nested class"

    val piston = mock<Piston>()
    every { piston.move() } returns "mocked"
    if (piston.move() != "mocked") return "Fail: class of a subpackage"

    val starter = mock<Starter>()
    every { starter.kick() } returns "mocked"
    if (starter.kick() != "mocked") return "Fail: final member of an abstract class"

    val spec = mock<Spec>()
    every { spec.describe() } returns "mocked"
    every { spec.size } returns 3
    if (spec.describe() != "mocked" || spec.size != 3) return "Fail: data class"
    if (Spec(1).copy(size = 2) != Spec(2)) return "Fail: data class members"

    val wheel = mock<Wheel>()
    every { wheel.spin() } returns "mocked"
    if (wheel.spin() != "mocked") return "Fail: class by name"

    val door = mock<Door>()
    every { door.open() } returns "mocked"
    if (door.open() != "mocked") return "Fail: annotated class"

    return checkModifiers()
}

private fun checkModifiers(): String {
    val engine = Engine::class.java
    if (engine.isFinal()) return "Fail: class was not opened"
    if (engine.isFinal("start")) return "Fail: function was not opened"
    if (engine.isFinal("getMode")) return "Fail: property was not opened"
    if (!engine.isFinal("id")) return "Fail: explicit final member was opened"
    if (!engine.isFinal("twice", Function0::class.java)) return "Fail: inline function was opened"
    if (!engine.isFinal("countdown", Int::class.javaPrimitiveType!!)) return "Fail: tailrec function was opened"
    if (!engine.isFinal("getRevision")) return "Fail: property with a private setter was opened"
    if (Engine(1).countdown(3) != 0 || Engine(1).serial != "serial" || Engine(1).code != "code") {
        return "Fail: members that stay final"
    }

    val finals = listOf(
        Engine.Companion::class.java,
        Locked::class.java,
        Rpm::class.java,
        Registry::class.java,
        Mode::class.java,
        WheelCover::class.java,
        Window::class.java,
        Gear::class.java,
    )
    finals.find { !it.isFinal() }?.let { return "Fail: ${it.name} was opened" }
    return "OK"
}
