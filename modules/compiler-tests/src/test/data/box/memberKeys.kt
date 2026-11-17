// CHECK_BYTECODE_TEXT

package sample

import io.lokki.mock

typealias Name = String

@JvmInline
value class Id(val value: Int)

class Ctx

interface Base<E> {
    fun add(element: E): Boolean
    fun <T> convert(element: E, to: T): T
}

interface Left {
    fun same(): String
}

interface Right {
    fun same(): String
}

interface Keys : Base<String>, Appendable, Left, Right {
    var open: Boolean
    fun plain(a: Int, b: String?)
    fun nullable(a: Int): String
    fun nullable(a: Int?): String
    fun <T : CharSequence> bound(x: T): String
    fun <T : Number> bound(x: T): String
    fun <T> twoBounds(x: T): String where T : CharSequence, T : Comparable<T>
    fun many(vararg x: Int): String
    fun array(x: IntArray): String
    fun generic(list: List<String>, map: Map<in Int, out Name?>, any: Array<*>): String
    fun alias(name: Name, id: Id): String
    fun function(block: (Int) -> String, extension: Int.() -> Unit, suspending: suspend () -> Unit): String
    fun String.extension(width: Int): String
    context(ctx: Ctx) fun contextual(): String
    context(ctx: Ctx) fun <T> T.everything(vararg x: T?): String
    suspend fun suspending(x: Int): String

    interface Nested {
        fun inner(): String
    }
}

class Outer<A : CharSequence> {
    open inner class Inner<B : Number> {
        open fun same(x: A): String = "outer"
        open fun same(x: B): String = "inner"
        open fun <C> all(a: A, b: B, c: C): String = "all"
    }
}

fun box(): String {
    mock<Keys>()
    mock<Keys.Nested>()
    mock<Outer<String>.Inner<Int>>()
    return "OK"
}

// 3 LDC "kotlin\.Any#equals\(kotlin\.Any\?\)"
// 3 LDC "kotlin\.Any#hashCode\(\)"
// 3 LDC "kotlin\.Any#toString\(\)"
// 1 LDC "sample\.Keys#<get-open>\(\)"
// 1 LDC "sample\.Keys#<set-open>\(kotlin\.Boolean\)"
// 1 LDC "sample\.Keys#plain\(kotlin\.Int,kotlin\.String\?\)"
// 1 LDC "sample\.Keys#nullable\(kotlin\.Int\)"
// 1 LDC "sample\.Keys#nullable\(kotlin\.Int\?\)"
// 1 LDC "sample\.Keys#<#0:kotlin\.CharSequence>bound\(#0\)"
// 1 LDC "sample\.Keys#<#0:kotlin\.Number>bound\(#0\)"
// 1 LDC "sample\.Keys#<#0:kotlin\.CharSequence&kotlin\.Comparable<#0>>twoBounds\(#0\)"
// 1 LDC "sample\.Keys#many\(vararg kotlin\.Int\)"
// 1 LDC "sample\.Keys#array\(kotlin\.IntArray\)"
// 1 LDC "sample\.Keys#generic\(kotlin\.collections\.List<kotlin\.String>,kotlin\.collections\.Map<in kotlin\.Int,out kotlin\.String\?>,kotlin\.Array<\*>\)"
// 1 LDC "sample\.Keys#alias\(kotlin\.String,sample\.Id\)"
// 1 LDC "sample\.Keys#function\(kotlin\.Function1<kotlin\.Int,kotlin\.String>,kotlin\.Function1<kotlin\.Int,kotlin\.Unit>,kotlin\.coroutines\.SuspendFunction0<kotlin\.Unit>\)"
// 1 LDC "sample\.Keys#kotlin\.String\.extension\(kotlin\.Int\)"
// 1 LDC "sample\.Keys#context\(sample\.Ctx\)contextual\(\)"
// 1 LDC "sample\.Keys#context\(sample\.Ctx\)<#0:kotlin\.Any\?>#0\.everything\(vararg #0\?\)"
// 1 LDC "sample\.Keys#suspending\(kotlin\.Int\)"
// 1 LDC "sample\.Base#add\(#0\)"
// 1 LDC "sample\.Base#<#1:kotlin\.Any\?>convert\(#0,#1\)"
// 1 LDC "java\.lang\.Appendable#append\(kotlin\.CharSequence\)"
// 1 LDC "java\.lang\.Appendable#append\(kotlin\.CharSequence,kotlin\.Int,kotlin\.Int\)"
// 1 LDC "java\.lang\.Appendable#append\(kotlin\.Char\)"
// 2 LDC "sample\.Left#same\(\)"
// 1 LDC "sample\.Right#same\(\)"
// 1 LDC "sample\.Keys\.Nested#inner\(\)"
// 1 LDC "sample\.Outer\.Inner#same\(#0\)"
// 1 LDC "sample\.Outer\.Inner#same\(#1\)"
// 1 LDC "sample\.Outer\.Inner#<#2:kotlin\.Any\?>all\(#0,#1,#2\)"
// 38 LDC "[^"]*#[^"]*\(
