// FILE: sample/JavaDefaults.java
package sample;

import java.util.List;

public interface JavaDefaults {
    String name();

    Integer boxed();

    int primitive();

    List<String> names();

    String[] array();

    Thread thread();

    <T> T query(String sql, List<T> rows);

    <T extends CharSequence> T first(List<T> values);

    void run();
}

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.verify
import java.util.Optional

interface Engine

enum class Color { RED }

@JvmInline
value class Code(val value: Int)

interface Defaults {
    val name: String
    var count: Int
    fun flag(): Boolean
    fun byte(): Byte
    fun short(): Short
    fun char(): Char
    fun long(): Long
    fun float(): Float
    fun double(): Double
    fun unit()
    fun nullableEngine(): Engine?
    fun nullableInt(): Int?
    fun nullableCode(): Code?
    fun iterable(): Iterable<Int>
    fun collection(): Collection<Int>
    fun list(): List<String>
    fun set(): Set<String>
    fun map(): Map<String, Int>
    fun sequence(): Sequence<Int>
    fun mutableList(): MutableList<String>
    fun mutableSet(): MutableSet<String>
    fun mutableMap(): MutableMap<String, Int>
    fun arrayList(): ArrayList<String>
    fun hashSet(): HashSet<String>
    fun linkedHashSet(): LinkedHashSet<String>
    fun hashMap(): HashMap<String, Int>
    fun linkedHashMap(): LinkedHashMap<String, Int>
    fun optional(): Optional<String>
    fun strings(): Array<String>
    fun lists(): Array<List<String>>
    fun nested(): Array<IntArray>
    fun ints(): IntArray
    fun chars(): CharArray
    fun <R> convertOrNull(): R?
    fun nothing(): Nothing
    fun engine(id: Int): Engine
    fun color(): Color
    fun code(): Code
    fun result(): Result<String>
    fun any(): Any
    fun pairs(): Pair<String, List<Int?>>
    fun <R> convert(): R
    fun <R> converted(): Array<R>
}

interface Box<T> {
    fun get(): T
    fun items(): List<T>
    fun values(): Array<T>
}

open class Service {
    open fun load(): String = "real"
    open fun size(): Int = 5
}

fun <T> boxOf(): Box<T> = mock<Box<T>>(relaxed = true)

private fun missing(block: () -> Any?): String =
    try {
        "returned ${block()}"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

private fun noDefault(type: String, call: String): String =
    "relaxed mock has no default for $type returned by $call; stub the call"

private fun noChild(type: String, call: String): String =
    noDefault(type, call) + " or annotate the file that creates the mock with @file:ChainedMocks"

fun box(): String {
    val defaults = mock<Defaults>(relaxed = true)
    val values = listOf(
        defaults.name, defaults.count, defaults.flag(), defaults.byte(), defaults.short(), defaults.char(),
        defaults.long(), defaults.float(), defaults.double(), defaults.unit(),
        defaults.nullableEngine(), defaults.nullableInt(), defaults.nullableCode(), defaults.convertOrNull<String>(),
    )
    val expected = listOf("", 0, false, 0.toByte(), 0.toShort(), '\u0000', 0L, 0f, 0.0, Unit, null, null, null, null)
    if (values != expected) return "Fail values: $values"
    defaults.count = 3

    val empties = listOf(
        defaults.iterable(), defaults.collection(), defaults.list(), defaults.set(), defaults.map().entries,
        defaults.sequence().toList(), defaults.mutableList(), defaults.mutableSet(), defaults.mutableMap().entries,
        defaults.arrayList(), defaults.hashSet(), defaults.linkedHashSet(), defaults.hashMap().entries,
        defaults.linkedHashMap().entries,
    )
    if (empties.any { it.iterator().hasNext() }) return "Fail empty collections: $empties"
    if (defaults.optional().isPresent) return "Fail optional"
    if (defaults.mutableList() === defaults.mutableList()) return "Fail: a mutable default is shared"
    defaults.mutableList().add("x")
    defaults.mutableSet().add("x")
    defaults.mutableMap()["x"] = 1
    val classes = listOf(
        defaults.mutableList(), defaults.mutableSet(), defaults.mutableMap(), defaults.arrayList(),
        defaults.hashSet(), defaults.linkedHashSet(), defaults.hashMap(), defaults.linkedHashMap(),
    ).map { it.javaClass.simpleName }
    val expectedClasses = listOf(
        "ArrayList", "LinkedHashSet", "LinkedHashMap", "ArrayList", "HashSet", "LinkedHashSet", "HashMap",
        "LinkedHashMap",
    )
    if (classes != expectedClasses) return "Fail mutable classes: $classes"

    val arrays = listOf<Any>(defaults.strings(), defaults.lists(), defaults.nested(), defaults.ints(), defaults.chars())
    if (arrays.map { it.javaClass } !=
        listOf(
            Array<String>::class.java, emptyArray<List<*>>().javaClass, Array<IntArray>::class.java,
            IntArray::class.java, CharArray::class.java,
        )
    ) {
        return "Fail array classes: ${arrays.map { it.javaClass }}"
    }
    if (defaults.strings().isNotEmpty() || defaults.ints().isNotEmpty()) return "Fail empty arrays"

    val messages = listOf(
        missing { defaults.nothing() },
        missing { defaults.engine(1) },
        missing { defaults.color() },
        missing { defaults.code() },
        missing { defaults.result() },
        missing { defaults.any() },
        missing { defaults.pairs() },
        missing { defaults.convert<String>() },
        missing { defaults.converted<String>() },
    )
    val expectedMessages = listOf(
        noDefault("Nothing", "Defaults.nothing()"),
        noChild("Engine", "Defaults.engine(1)"),
        noDefault("Color", "Defaults.color()"),
        noDefault("Code", "Defaults.code()"),
        noDefault("Result<String>", "Defaults.result()"),
        noChild("Any", "Defaults.any()"),
        noDefault("Pair<String, List<Int?>>", "Defaults.pairs()"),
        noDefault("type parameter R", "Defaults.convert()"),
        noDefault("Array<R>", "Defaults.converted()"),
    )
    if (messages != expectedMessages) return "Fail messages: $messages"

    every { defaults.name } returns "stubbed"
    every { defaults.engine(1) } returns object : Engine {}
    if (defaults.name != "stubbed") return "Fail: a stub answers before the default"
    defaults.engine(1)
    verify { defaults.unit() }
    verify { defaults.count = 3 }

    if (mock<Box<String>>(relaxed = true).get() != "") return "Fail substituted type argument"
    if (mock<Box<Int?>>(relaxed = true).get() != null) return "Fail nullable type argument"
    if (mock<Box<Array<String>>>(relaxed = true).get().javaClass != Array<String>::class.java) {
        return "Fail array type argument"
    }
    val box = boxOf<String>()
    if (box.items() != emptyList<String>()) return "Fail list of a type parameter"
    val boxMessages = listOf(missing { box.get() }, missing { box.values() })
    if (boxMessages != listOf(noDefault("type parameter T", "Box.get()"), noDefault("Array<T>", "Box.values()"))) {
        return "Fail type parameter messages: $boxMessages"
    }

    val callback = mock<() -> Unit>(relaxed = true)
    callback()
    verify { callback() }
    if (mock<(Int) -> String>(relaxed = true)(1) != "") return "Fail function type"

    val service = mock<Service>(relaxed = true)
    if (service.load() != "" || service.size() != 0) return "Fail open class: ${service.load()} ${service.size()}"

    val java = mock<JavaDefaults>(relaxed = true)
    java.run()
    if (java.name() != "" || java.boxed() != 0 || java.primitive() != 0) return "Fail platform types"
    if (java.names().javaClass != ArrayList::class.java || !java.names().add("x")) return "Fail Java list"
    if (java.array().javaClass != Array<String>::class.java) return "Fail Java array"
    val thread = missing { java.thread() }
    if (thread != noChild("Thread", "JavaDefaults.thread()")) return "Fail Java message: $thread"
    val row: String? = java.query("select", listOf("a"))
    val first: String? = java.first(listOf("b"))
    if (row != null || first != null) return "Fail Java type parameter: $row $first"

    return "OK"
}
