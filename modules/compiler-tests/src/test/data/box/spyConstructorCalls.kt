// WITH_STDLIB
package sample

import io.lokki.every
import io.lokki.spy
import io.lokki.verify
import java.util.Random

open class Point(val x: Int = 1, val y: Int = 2, val label: String = "p") {
    open fun describe(): String = "$label($x, $y)"
}

open class Tags(vararg val tags: String) {
    open fun count(): Int = tags.size
}

open class Box<T>(val value: T) {
    open fun get(): T = value
}

@JvmInline
value class Code(val value: Int)

open class Lock(val code: Code) {
    open fun open(code: Code): Boolean = code == this.code
}

class Outer(val prefix: String) {
    open inner class Inner(val id: Int) {
        open fun name(): String = "$prefix$id"
    }
}

var evaluated = ""

fun arg(name: String, value: Int): Int {
    evaluated += name
    return value
}

fun label(): String {
    evaluated += "n"
    return "named"
}

inline fun <reified T : Any> spyOf(value: T): Box<T> = spy(Box(value))

fun box(): String {
    val reordered = spy(Point(y = arg("y", 4), x = arg("x", 3)), name = label())
    if (evaluated != "yxn") return "Fail evaluation order: $evaluated"
    if (reordered.describe() != "p(3, 4)") return "Fail reordered: ${reordered.describe()}"
    if (!reordered.toString().startsWith("Point(named#")) return "Fail name: $reordered"

    evaluated = ""
    val reorderedSpy = spy(name = label(), obj = Point(x = arg("x", 5)))
    if (evaluated != "nx") return "Fail order of reordered spy arguments: $evaluated"
    if (reorderedSpy.javaClass.superclass != Point::class.java || reorderedSpy.describe() != "p(5, 2)") {
        return "Fail reordered spy arguments: ${reorderedSpy.describe()}"
    }
    if (!reorderedSpy.toString().startsWith("Point(named#")) return "Fail reordered name: $reorderedSpy"
    val withBlock = spy(Point(arg("x", 6)), block = { every { describe() } returns "block" }, name = "late")
    if (withBlock.describe() != "block" || !withBlock.toString().startsWith("Point(late#")) return "Fail block before name"

    val omitted = spy(Point(label = "q"))
    if (omitted.describe() != "q(1, 2)") return "Fail omitted: ${omitted.describe()}"
    val other = spy(Point(y = 5))
    if (other.describe() != "p(1, 5)") return "Fail other omitted: ${other.describe()}"
    if (other.javaClass == omitted.javaClass) return "Fail shapes share a class"
    if (spy(Point(x = 7, y = 8)).javaClass != reordered.javaClass) return "Fail same shape"

    if (spy(Tags("a", "b")).count() != 2) return "Fail vararg"
    if (spy(Tags(*arrayOf("a", "b", "c"))).count() != 3) return "Fail spread"
    if (spy(Tags()).count() != 0) return "Fail empty vararg"

    val box = spy(Box("x"))
    if (box.get() != "x") return "Fail generic: ${box.get()}"
    every { box.get() } returns "y"
    if (box.get() != "y") return "Fail generic stub"
    if (spyOf(5).get() != 5) return "Fail type parameter"

    val lock = spy(Lock(Code(7)))
    if (!lock.open(Code(7)) || lock.open(Code(8))) return "Fail value class"

    val inner = spy(Outer("o").Inner(3))
    if (inner.name() != "o3") return "Fail inner: ${inner.name()}"

    val suffix = "!"
    open class Local(val text: String) {
        open fun shout(): String = text + suffix
    }
    val local = spy(Local("hi"))
    if (local.shout() != "hi!") return "Fail local: ${local.shout()}"
    every { local.shout() } returns "quiet"
    if (local.shout() != "quiet") return "Fail local stub"

    val random = spy(Random(42))
    val expected = Random(42).nextInt(100)
    if (random.nextInt(100) != expected) return "Fail Java class"
    every { random.nextInt(100) } returns -1
    if (random.nextInt(100) != -1) return "Fail Java stub"
    verify(exactly = 2) { random.nextInt(100) }

    return "OK"
}
