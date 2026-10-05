package sample

import io.lokki.AnswerScope
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import java.io.InputStream

@JvmInline
value class Code(val value: Int)

val log = ArrayList<String>()

open class Engine {
    open fun greet(name: String): String = "hello $name"

    open fun add(a: Int, b: Long): Long = a + b

    open fun unlock(code: Code): Boolean = code.value == 42

    open fun <T : CharSequence> length(value: T): Int = value.length

    open fun twice(): String = greet("a") + greet("b")

    open fun record(message: String) {
        log += message
    }

    open val name: String
        get() = "engine"

    open var speed: Int = 0

    open fun String.shout(): String = uppercase()

    open fun start(): String = "started"

    override fun toString(): String = "Engine"
}

open class Plain

interface Greeter {
    fun name(): String

    fun greet(): String = "hi ${name()}"
}

abstract class Base : Greeter

interface Polite : Greeter

abstract class Pump {
    abstract fun pressure(): Int
}

fun AnswerScope<Int>.original(): Int = callOriginal()

fun box(): String {
    val engine = mock<Engine>()
    every { engine.greet(any()) } answers { callOriginal() + "!" }
    every { engine.add(any(), any()) } answers { callOriginal() * 10 }
    every { engine.unlock(any()) } answers { callOriginal() }
    every { engine.length<String>(any()) } answers { callOriginal() + 1 }
    every { engine.twice() } answers { callOriginal() }
    every { engine.record(any()) } answers { callOriginal() }
    every { engine.name } answers { callOriginal() + "?" }
    every { engine.speed = any() } answers { callOriginal() }
    every { engine.speed } answers { callOriginal() }
    with(engine) { every { any<String>().shout() } answers { callOriginal() } }
    every { engine.start() } returns "stubbed" andThenAnswer { callOriginal() }
    every { engine.toString() } answers { callOriginal() }

    if (engine.greet("x") != "hello x!") return "Fail: greet ${engine.greet("x")}"
    if (engine.add(2, 3L) != 50L) return "Fail: primitives"
    if (!engine.unlock(Code(42)) || engine.unlock(Code(1))) return "Fail: value class"
    if (engine.length("abc") != 4) return "Fail: generic member"
    if (engine.twice() != "hello a!hello b!") return "Fail: original calls the mock: ${engine.twice()}"
    verify { engine.greet("a") }
    engine.record("recorded")
    if (log != listOf("recorded")) return "Fail: Unit member $log"
    if (engine.name != "engine?") return "Fail: getter"
    engine.speed = 5
    if (engine.speed != 5) return "Fail: setter and getter ${engine.speed}"
    if (with(engine) { "a".shout() } != "A") return "Fail: member extension"
    if (engine.start() != "stubbed" || engine.start() != "started") return "Fail: andThenAnswer"
    if (engine.toString() != "Engine") return "Fail: toString of a class $engine"

    val plain = mock<Plain>()
    every { plain.toString() } answers { callOriginal() }
    every { plain.hashCode() } answers { callOriginal() }
    every { plain.equals(any()) } answers { callOriginal() }
    if (plain.toString() != plain.javaClass.name + "@" + Integer.toHexString(System.identityHashCode(plain))) {
        return "Fail: toString of Any $plain"
    }
    if (plain.hashCode() != System.identityHashCode(plain)) return "Fail: hashCode of Any"
    if (plain != plain || plain == mock<Plain>()) return "Fail: equals of Any"

    val greeter = mock<Greeter>()
    every { greeter.name() } returns "ann"
    every { greeter.greet() } answers { callOriginal() }
    every { greeter.toString() } answers { callOriginal() }
    if (greeter.greet() != "hi ann") return "Fail: default method ${greeter.greet()}"
    if (!greeter.toString().startsWith(greeter.javaClass.name + "@")) return "Fail: toString of interface $greeter"

    val base = mock<Base>()
    every { base.name() } returns "bob"
    every { base.greet() } answers { callOriginal() }
    if (base.greet() != "hi bob") return "Fail: default method through a class"

    val polite = mock<Polite>()
    every { polite.name() } returns "cid"
    every { polite.greet() } answers { callOriginal() }
    if (polite.greet() != "hi cid") return "Fail: default method of a superinterface"

    val stream = mock<InputStream>()
    every { stream.read() } returnsMany listOf(1, 2, -1)
    every { stream.read(any()) } answers { callOriginal() }
    every { stream.read(any(), any(), any()) } answers { callOriginal() }
    val bytes = ByteArray(4)
    if (stream.read(bytes) != 2 || bytes[0] != 1.toByte() || bytes[1] != 2.toByte()) return "Fail: Java class"

    val pump = mock<Pump>()
    every { pump.pressure() } answers { original() }
    try {
        pump.pressure()
        return "Fail: no exception"
    } catch (e: LokkiException) {
        if (e.message != "no original implementation for $pump.pressure()") return "Fail message: ${e.message}"
    }

    val dispatcher = engine.javaClass.getDeclaredMethod("lokki\$original", Int::class.java, Array<Any?>::class.java)
    if (!dispatcher.isSynthetic) return "Fail: lokki\$original is not synthetic"

    return "OK"
}
