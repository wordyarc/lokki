package io.lokki

public fun interface Answer<out T> {
    public fun answer(call: Call): T
}

public class Call internal constructor(public val invocation: Invocation)

public class Invocation internal constructor(public val self: Any, public val args: List<Any?>)

@LokkiDslMarker
public class AnswerScope<T> internal constructor(public val call: Call) {
    public val invocation: Invocation
        get() = call.invocation

    public val self: Any
        get() = invocation.self

    public val args: List<Any?>
        get() = invocation.args

    public val nArgs: Int
        get() = args.size

    public val nothing: Nothing?
        get() = null

    public inline fun <reified R> firstArg(): R = args[0] as R

    public inline fun <reified R> secondArg(): R = args[1] as R

    public inline fun <reified R> thirdArg(): R = args[2] as R

    public inline fun <reified R> lastArg(): R = args.last() as R

    public inline fun <reified R> arg(n: Int): R = args[n] as R

    public fun <E> MutableList<E>.captured(): E = last()
}
