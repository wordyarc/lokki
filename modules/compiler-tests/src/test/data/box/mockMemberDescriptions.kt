// FILE: lib/HiddenBase.java
package lib;

abstract class HiddenBase {
    public String inherited() {
        return "inherited";
    }
}

// FILE: lib/Exposed.java
package lib;

public abstract class Exposed extends HiddenBase {
}

// FILE: lib/JavaApi.java
package lib;

public interface JavaApi {
    String name();
}

// FILE: test.kt
package sample

import io.lokki.MethodDescription
import io.lokki.every
import io.lokki.mock
import lib.Exposed
import lib.JavaApi

@JvmInline
value class Amount(val cents: Long)

class Ctx

interface Base {
    fun base(id: Int): String
}

interface Repo<T> {
    fun save(item: T): T
}

open class Ledger {
    internal open fun audit(id: Int): String = ""
}

interface Api : Base {
    var isOpen: Boolean
    val size: Int
    fun find(id: Int, deleted: Boolean = false): String?
    fun fail(): Nothing
    fun reset()
    fun pay(amount: Amount): Amount
    fun route(vararg stops: String): List<String>
    fun Int.sum(prefix: Long, vararg values: Int): IntArray
    fun <T : CharSequence> echo(value: T, all: Array<T>): T
    context(ctx: Ctx)
    fun load(): Any
    suspend fun refresh(): Int
}

private fun MethodDescription.render(): String =
    "${declaringClass.simpleName}.$name(${argsToStr()}): ${returnType.simpleName}" +
        listOf(
            "nullable" to returnTypeNullable,
            "unit" to returnsUnit,
            "nothing" to returnsNothing,
            "suspend" to isSuspend,
            "fn" to isFnCall,
            "private" to privateCall,
        ).filter { it.second }.joinToString("") { " ${it.first}" } +
        if (varArgsArg >= 0) " vararg $varArgsArg" else ""

private fun descriptions(mock: Any): Map<String, MethodDescription> {
    val describe = mock.javaClass.getMethod("lokki\$describe", Int::class.javaPrimitiveType)
    return generateSequence(0) { it + 1 }
        .map { describe.invoke(mock, it) as MethodDescription? }
        .takeWhile { it != null }
        .associateBy { it!!.toString() }
        .mapValues { it.value!! }
}

private fun rendered(mock: Any): Map<String, String> = descriptions(mock).mapValues { it.value.render() }

fun box(): String {
    val api = mock<Api>()
    val expected = mapOf(
        "equals(Any)" to "Any.equals(Any): Boolean",
        "hashCode()" to "Any.hashCode(): Int",
        "toString()" to "Any.toString(): String",
        "base(Int)" to "Base.base(Int): String",
        "isOpen()" to "Api.isOpen(): Boolean",
        "setOpen(Boolean)" to "Api.setOpen(Boolean): Unit unit",
        "getSize()" to "Api.getSize(): Int",
        "find(Int, Boolean)" to "Api.find(Int, Boolean): String nullable",
        "fail()" to "Api.fail(): Void nothing",
        "reset()" to "Api.reset(): Unit unit",
        "pay(Amount)" to "Api.pay(Amount): Amount",
        "route(Array)" to "Api.route(Array): List vararg 0",
        "sum(Int, Long, IntArray)" to "Api.sum(Int, Long, IntArray): IntArray vararg 2",
        "echo(CharSequence, Array)" to "Api.echo(CharSequence, Array): CharSequence",
        "load(Ctx)" to "Api.load(Ctx): Any",
        "refresh()" to "Api.refresh(): Int suspend",
    )
    val actual = rendered(api)
    if (actual != expected) return "Fail descriptions: $actual"

    val methods = ArrayList<MethodDescription>()
    every { api.find(any(), any()) } answers {
        methods += method
        methods += call.invocation.method
        call.retType.simpleName
    }
    if (api.find(1) != "String") return "Fail retType"
    val (first, second) = methods
    if (first !== second || first.paramTypes != listOf(Int::class, Boolean::class)) return "Fail method: $methods"
    val other = descriptions(mock<Api>())
    if (other.getValue("find(Int, Boolean)") != first || other.getValue("base(Int)") == first) return "Fail equals"
    val (equals, hashCode, toString) = descriptions(api).values.toList()
    if (!equals.isEquals() || !hashCode.isHashCode() || !toString.isToString() || first.isEquals()) {
        return "Fail methods of Any"
    }

    val repo = rendered(mock<Repo<String>>())["save(String)"]
    if (repo != "Repo.save(String): String") return "Fail generic: $repo"
    val function = rendered(mock<(Int) -> String>())["invoke(Int)"]
    if (function != "Function1.invoke(Int): String fn") return "Fail function: $function"
    val audit = rendered(mock<Ledger>())["audit(Int)"]
    if (audit != "Ledger.audit(Int): String") return "Fail internal: $audit"
    val javaApi = rendered(mock<JavaApi>())["name()"]
    if (javaApi != "JavaApi.name(): String") return "Fail Java: $javaApi"

    val exposed = mock<Exposed>()
    val inherited = rendered(exposed)["inherited()"]
    if (inherited != "Exposed.inherited(): String") return "Fail inherited: $inherited"
    every { exposed.inherited() } answers { method.declaringClass.simpleName + " " + callOriginal() }
    if (exposed.inherited() != "Exposed inherited") return "Fail inherited answer: ${exposed.inherited()}"
    return "OK"
}
