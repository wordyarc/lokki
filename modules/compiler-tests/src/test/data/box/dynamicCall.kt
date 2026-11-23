// WITH_KOTLINX_COROUTINES
// FILE: other/JavaTemplate.java
package other;

public abstract class JavaTemplate {
    public String run() {
        return hook() + ":" + open(1);
    }

    protected abstract String hook();

    protected String open(int value) {
        return "real" + value;
    }
}

// FILE: test.kt
package sample

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.justRun
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import kotlinx.coroutines.runBlocking
import other.JavaTemplate

@JvmInline
value class Amount(val value: Int)

abstract class Template {
    fun run(): String = "result=" + hook()
    fun combine(text: String, value: Int): String = withArguments(text, value)
    fun pick(first: Int, second: String): String = picked(first, second)
    fun ofInt(): String = overloaded(1)
    fun ofText(): String = overloaded("a")
    fun effect() = sideEffect()
    fun charge(amount: Amount): Amount = valued(amount)
    suspend fun load(): Int = suspending(1)

    protected abstract fun hook(): String
    protected open fun withArguments(text: String, value: Int): String = "real:$text:$value"
    protected open fun picked(first: Int, second: String): String = "real"
    protected open fun overloaded(value: Int): String = "int"
    protected open fun overloaded(value: String): String = "text"
    protected open fun sideEffect() {
        throw IllegalStateException("real side effect")
    }
    protected open fun valued(amount: Amount): Amount = amount
    protected abstract suspend fun suspending(value: Int): Int
    open fun visible(value: Int): Int = value
}

open class Concrete : Template() {
    override fun hook(): String = "concrete"
    override suspend fun suspending(value: Int): Int = value
}

abstract class Generic<T> {
    fun call(value: T): T = take(value)
    protected abstract fun take(value: T): T
}

interface Store {
    operator fun get(key: String): String
    operator fun get(index: Int): String
}

fun box(): String {
    val store = mock<Store> {
        every { get("implicit") } returns "member"
    }
    every { store["explicit"] } returns "indexed"
    every { store[1] } returns "by index"
    if (store["implicit"] != "member") return "implicit member get"
    if (store["explicit"] != "indexed") return "explicit member get"
    if (store[1] != "by index") return "member get of Int"
    verify { store["explicit"] }

    val template = mock<Template>()
    every { template["hook"]() } returns "stub"
    if (template.run() != "result=stub") return "no arguments: ${template.run()}"
    verify(exactly = 1) { template["hook"]() }

    every { template["withArguments"](any(), 5) } answers { firstArg<String>() + arg<Int>(1) }
    if (template.combine("a", 5) != "a5") return "matcher and value"
    every { template["picked"](any(), any()) } returnsArgument 1
    if (template.pick(1, "b") != "b") return "returnsArgument"

    every { template["overloaded"](1) } returns "stub int"
    every { template["overloaded"](any<String>()) } returns "stub text"
    if (template.ofInt() != "stub int") return "overload of Int"
    if (template.ofText() != "stub text") return "overload of String"

    justRun { template["sideEffect"]() }
    template.effect()
    verify { template["sideEffect"]() }

    every { template["valued"](Amount(1)) } returns Amount(2)
    if (template.charge(Amount(1)) != Amount(2)) return "value class"

    every { template["visible"](any()) } returns 8
    if (template.visible(1) != 8) return "public member"

    coEvery { template["suspending"](any()) } returns 7
    if (runBlocking { template.load() } != 7) return "suspend"
    coVerify { template["suspending"](1) }

    val original = mock<Concrete>()
    every { original["withArguments"](any(), any()) } answers { callOriginal() }
    if (original.combine("x", 1) != "real:x:1") return "callOriginal: ${original.combine("x", 1)}"

    val spied = spy(Concrete())
    if (spied.run() != "result=concrete") return "spy"
    every { spied["hook"]() } returns "spied"
    if (spied.run() != "result=spied") return "stub of a spy"

    val excluded = mock<Template>()
    every { excluded["hook"]() } returns "x"
    excluded.run()
    excludeRecords { excluded["hook"]() }
    verify(exactly = 0) { excluded["hook"]() }

    val generic = mock<Generic<String>>()
    every { generic["take"]("a") } returns "b"
    if (generic.call("a") != "b") return "generic member"

    val java = mock<JavaTemplate>()
    every { java.run() } answers { callOriginal() }
    every { java["hook"]() } returns "java"
    every { java["open"](any<Int>()) } answers { callOriginal() }
    if (java.run() != "java:real1") return "protected member of a Java class: ${java.run()}"
    return "OK"
}
