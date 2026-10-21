package io.lokki

import io.lokki.internal.Abandoned
import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.toStr
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass

public fun interface Answer<out T> {
    public fun answer(call: Call): T

    public suspend fun coAnswer(call: Call): T = answer(call)
}

public class Call internal constructor(public val invocation: Invocation, private val lambda: Int) {
    public val retType: KClass<*>
        get() = invocation.method.returnType

    internal fun <F> capturedLambda(function: String, matcher: String): CapturingSlot<F> {
        if (lambda < 0) {
            throw LokkiException(
                "$function() in the answer of ${invocation.describe()} needs $matcher() " +
                    "as an argument of the stubbed call"
            )
        }
        @Suppress("UNCHECKED_CAST")
        return CapturingSlot<F>().apply { captured = invocation.args[lambda] as F }
    }
}

public class Invocation internal constructor(
    public val self: Any,
    private val member: Int,
    private val arguments: Array<Any?>,
) {
    public val method: MethodDescription
        get() = (self as GeneratedMock).`lokki$state`.description(member)

    public val args: List<Any?> = ArgumentList(arguments)

    internal fun callOriginal(): Any? {
        val mock = self as GeneratedMock
        if (!method.isSuspend) return mock.`lokki$original`(member, arguments)
        val result = suspend { mock.`lokki$coOriginal`(member, arguments) }.startCoroutineUninterceptedOrReturn(Abandoned)
        if (result === COROUTINE_SUSPENDED) {
            throw LokkiException("callOriginal() of suspend ${describe()} suspended; use coCallOriginal()")
        }
        return result
    }

    internal suspend fun coCallOriginal(): Any? {
        val mock = self as GeneratedMock
        return if (method.isSuspend) {
            mock.`lokki$coOriginal`(member, arguments)
        } else {
            mock.`lokki$original`(member, arguments)
        }
    }

    internal fun describe(): String =
        (self as GeneratedMock).`lokki$state`.call(member, arguments.joinToString { it.toStr() })
}

@LokkiDslMarker
public class AnswerScope<T> internal constructor(public val call: Call) {
    public val invocation: Invocation
        get() = call.invocation

    public val self: Any
        get() = invocation.self

    public val method: MethodDescription
        get() = invocation.method

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

    public fun <F : Function<*>> lambda(): CapturingSlot<F> = call.capturedLambda("lambda", "captureLambda")

    public fun <F : Any> coroutine(): CapturingSlot<F> = call.capturedLambda("coroutine", "captureCoroutine")

    @Suppress("UNCHECKED_CAST")
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public fun callOriginal(): T = invocation.callOriginal() as T

    @Suppress("UNCHECKED_CAST")
    @LokkiIntrinsic(LokkiIntrinsic.Kind.ANSWER)
    public suspend fun coCallOriginal(): T = invocation.coCallOriginal() as T
}

private class ArgumentList(private val arguments: Array<Any?>) : AbstractList<Any?>() {
    override val size: Int
        get() = arguments.size

    override fun get(index: Int): Any? = arguments[index]
}
