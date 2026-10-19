package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import java.util.function.Function

interface Box<T> {
    fun get(): T
    fun put(value: T): Boolean
    fun <R> map(transform: (T) -> R): R
    fun find(value: T, limit: Int = 10): Int
    fun describe(value: T): String = "box of $value"
    val first: T
}

abstract class Holder<T>(val initial: T) {
    abstract fun hold(): T

    open fun replace(value: T): T = value
}

open class Labeled<A, B : CharSequence>(val a: A, val b: B) {
    open fun label(): B = b
}

@JvmInline
value class Code(val value: Int)

interface Source<out T> {
    fun next(): T
}

interface Ranged<T : Number> {
    fun value(): T
}

interface Pairs<A, B : List<A>> {
    fun second(): B
}

interface Overloads<T> {
    fun f(x: T): String
    fun f(x: String): String
}

class Outer<T> {
    open inner class Inner {
        open fun value(): T? = null
    }
}

fun <T> callGeneric(overloads: Overloads<T>, value: T): String = overloads.f(value)

fun <T> stubGeneric(overloads: Overloads<T>, value: T) {
    every { overloads.f(value) } returns "generic"
}

fun stubString(overloads: Overloads<Int>, value: String) {
    every { overloads.f(value) } returns "string"
}

fun box(): String {
    val strings = mock<Box<String>>()
    if (strings.javaClass.name != "sample.Box\$LokkiMock\$0f829f35\$MockGenericTypeKt") {
        return "Fail name: ${strings.javaClass.name}"
    }
    every { strings.get() } returns "a"
    every { strings.put(any()) } returns true
    every { strings.find("x") } returns 1
    every { strings.find("x", 3) } returns 3
    every { strings.describe(any()) } answers { callOriginal() + "!" }
    every { strings.first } returns "f"
    every { strings.map<Int>(any()) } returns 7
    if (strings.get() != "a" || !strings.put("z") || strings.first != "f") return "Fail stubs"
    if (strings.find("x") != 1 || strings.find("x", 3) != 3) return "Fail default argument"
    if (strings.describe("q") != "box of q!") return "Fail original: ${strings.describe("q")}"
    if (strings.map { it.length } != 7) return "Fail generic member"
    if ((strings as Box<*>).get() != "a") return "Fail call through the erased type"
    verify(exactly = 1) { strings.put("z") }

    val ints = mock<Box<Int>>()
    every { ints.get() } returns 5
    every { ints.put(more(3)) } returns true
    if (ints.get() != 5 || !ints.put(4)) return "Fail Int argument"
    val nullable = mock<Box<String?>>()
    every { nullable.get() } returns null
    if (nullable.get() != null) return "Fail nullable argument"
    val names = listOf(ints, nullable, mock<Box<String>>()).map { it.javaClass.simpleName }
    val expected = listOf("6648d247", "e0d147c8", "0f829f35").map { "Box\$LokkiMock\$$it\$MockGenericTypeKt" }
    if (names != expected) return "Fail names: $names"

    val holder = mock<Holder<Int>>()
    every { holder.hold() } returns 3
    every { holder.replace(any()) } answers { callOriginal() + 1 }
    if (holder.hold() != 3 || holder.replace(4) != 5) return "Fail generic class"
    val labeled = mock<Labeled<Long, String>>()
    every { labeled.label() } returns "l"
    if (labeled.label() != "l") return "Fail generic class with a bounded parameter"
    val coded = mock<Box<Code>>()
    every { coded.get() } returns Code(2)
    if (coded.get() != Code(2)) return "Fail value class argument"

    val comparator = mock<Comparator<in String>>()
    every { comparator.compare("a", "b") } returns -1
    if (comparator.compare("a", "b") != -1) return "Fail in projection"
    val source = mock<Source<out CharSequence>>()
    every { source.next() } returns "n"
    if (source.next() != "n") return "Fail out projection"
    if (source.javaClass.simpleName != "Source\$LokkiMock\$a1875a37\$MockGenericTypeKt") return "Fail approximated name"
    val ranged = mock<Ranged<*>>()
    every { ranged.value() } returns 1.5
    if (ranged.value() != 1.5) return "Fail star projection"
    val pairs = mock<Pairs<String, *>>()
    every { pairs.second() } returns listOf("x")
    if (pairs.second() != listOf("x")) return "Fail star projection with a dependent bound"

    val function = mock<Function<String, Int>>()
    every { function.apply("a") } returns 1
    every { function.andThen<Int>(any()) } answers { callOriginal() }
    if (function.andThen { it + 1 }.apply("a") != 2) return "Fail default method of a Java interface"

    val overloads = mock<Overloads<String>>()
    val methods = overloads.javaClass.declaredMethods.filter { it.name == "f" }
        .associate { it.parameterTypes.single().name to it.isBridge }
    if (methods != mapOf("java.lang.String" to false, "java.lang.Object" to true)) return "Fail overloads: $methods"
    stubGeneric(overloads, "a")
    if (callGeneric(overloads, "a") != "generic") return "Fail overload through the type parameter"
    @Suppress("UNCHECKED_CAST")
    stubString(overloads as Overloads<Int>, "b")
    if (callGeneric(overloads, "b") != "string") return "Fail overload through the declared type"

    val inner = mock<Outer<String>.Inner>()
    every { inner.value() } returns "v"
    if (inner.value() != "v") return "Fail inner class of a generic class"
    return "OK"
}
