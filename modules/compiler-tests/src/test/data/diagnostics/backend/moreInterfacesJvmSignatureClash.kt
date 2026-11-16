package sample

import io.lokki.mock

interface Strings {
    fun f(x: List<String>): Int
}

interface Ints {
    fun f(x: List<Int>): Int
}

interface Named {
    val name: String
}

interface NameGetter {
    fun getName(): String
}

fun test() {
    <!CONFLICTING_JVM_DECLARATIONS!>mock<Strings>(moreInterfaces = arrayOf(Ints::class))<!>
    <!CONFLICTING_JVM_DECLARATIONS!>mock<Named>(moreInterfaces = arrayOf(NameGetter::class))<!>
}
