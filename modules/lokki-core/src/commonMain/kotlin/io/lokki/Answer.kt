package io.lokki

import io.lokki.internal.Abandoned
import io.lokki.internal.FieldAccess
import io.lokki.internal.GeneratedMock
import io.lokki.internal.LokkiIntrinsic
import io.lokki.internal.StubEntry
import io.lokki.internal.identityHashCode
import io.lokki.internal.toStr
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass

public fun interface Answer<out T> {
    public fun answer(call: Call): T

    public suspend fun coAnswer(call: Call): T = answer(call)
}

public class Call internal constructor(public val invocation: Invocation, private val stub: StubEntry) {
    public val retType: KClass<*>
        get() = invocation.method.returnType

    public val matcher: InvocationMatcher
        get() = stub.invocationMatcher(invocation.self, invocation.member)

    override fun equals(other: Any?): Boolean =
        this === other || other is Call && invocation == other.invocation && matcher == other.matcher

    override fun hashCode(): Int = 31 * invocation.hashCode() + matcher.hashCode()

    override fun toString(): String = "Call(retType=${retType.simpleName}, invocation=$invocation, matcher=$matcher)"

    internal fun <F> capturedLambda(function: String, matcher: String): CapturingSlot<F> {
        val lambda = stub.lambda
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
    internal val member: Int,
    private val arguments: Array<Any?>,
) {
    public val method: MethodDescription
        get() = (self as GeneratedMock).`lokki$state`().description(member)

    public val args: List<Any?> = ReadOnlyList(arguments)

    public val originalCall: () -> Any?
        get() = { callOriginal() }

    override fun equals(other: Any?): Boolean =
        this === other || other is Invocation && self === other.self && method == other.method && args == other.args

    override fun hashCode(): Int = 31 * (31 * identityHashCode(self) + method.hashCode()) + args.hashCode()

    override fun toString(): String = describe()

    internal fun callOriginal(): Any? {
        val mock = self as GeneratedMock
        if (!method.isSuspend) return mock.`lokki$original`(member, arguments)
        val result = suspend { mock.`lokki$coOriginal`(member, arguments) }.startCoroutineUninterceptedOrReturn(Abandoned)
        if (result === COROUTINE_SUSPENDED) {
            throw LokkiException("callOriginal() of suspend ${describe()} suspended; use coCallOriginal()")
        }
        return result
    }

    internal fun readField(): Any? =
        (self as GeneratedMock).`lokki$original`(self.`lokki$state`().fieldAccessor(member, FieldAccess.READ), emptyArray())

    internal fun writeField(value: Any?) {
        val mock = self as GeneratedMock
        mock.`lokki$original`(mock.`lokki$state`().fieldAccessor(member, FieldAccess.WRITE), arrayOf(value))
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
        (self as GeneratedMock).`lokki$state`().call(member, arguments.joinToString { it.toStr() })
}

public class InvocationMatcher internal constructor(
    public val self: Any,
    private val member: Int,
    matchers: Array<Matcher<Any?>>,
) {
    public val method: MethodDescription
        get() = (self as GeneratedMock).`lokki$state`().description(member)

    public val args: List<Matcher<Any>> = ReadOnlyList(matchers)

    override fun equals(other: Any?): Boolean =
        this === other || other is InvocationMatcher && self === other.self && method == other.method &&
            args == other.args

    override fun hashCode(): Int = 31 * (31 * identityHashCode(self) + method.hashCode()) + args.hashCode()

    override fun toString(): String = (self as GeneratedMock).`lokki$state`().call(member, args.joinToString())
}

@LokkiDslMarker
public class AnswerScope<T, B> internal constructor(public val call: Call) {
    public val invocation: Invocation
        get() = call.invocation

    public val matcher: InvocationMatcher
        get() = call.matcher

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

    @Suppress("UNCHECKED_CAST")
    public val value: B
        get() = valueAny as B

    public val valueAny: Any?
        get() = args[0]

    @Suppress("UNCHECKED_CAST")
    public var fieldValue: B
        get() = fieldValueAny as B
        set(value) {
            fieldValueAny = value
        }

    public var fieldValueAny: Any?
        get() = invocation.readField()
        set(value) {
            invocation.writeField(value)
        }

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

private class ReadOnlyList<E>(private val elements: Array<out E>) : AbstractList<E>() {
    override val size: Int
        get() = elements.size

    override fun get(index: Int): E = elements[index]
}
