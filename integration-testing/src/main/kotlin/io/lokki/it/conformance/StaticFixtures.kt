package io.lokki.it.conformance

infix fun Int.op(b: Int) = this + b

infix fun Int.oper(b: Int) = this + b

val Int.selfOp get() = this * this

data class IntWrapper(
    val data: Int,
)

object ExtObj {
    fun IntWrapper.h() = data + 5
}

fun IntWrapper.f() = data + 5

fun topLevelFn() = 55

fun flagTestTopLevel() = 0

@JvmSynthetic
fun syntheticTopLevelFunction(arg: String): String = "real-$arg"

fun functionWithDefaultArgument(a: Int = 5): Int = a * 10
