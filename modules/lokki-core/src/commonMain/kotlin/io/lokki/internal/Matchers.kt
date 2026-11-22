package io.lokki.internal

import io.lokki.AssertScope
import io.lokki.CapturingSlot
import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.VarargScope
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass

@PublishedApi
internal object AnyMatcher : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = true

    override fun toString(): String = "any()"
}

@PublishedApi
internal class EqMatcher(internal val value: Any?) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = deepEquals(arg, value)

    override fun toString(): String = "eq(${value.toStr()})"
}

@PublishedApi
internal class ArrayMatcher(internal val elements: Array<Matcher<Any?>>) :
    Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean {
        val actual = arg.arrayElements() ?: return false
        return actual.size == elements.size && elements.indices.all { elements[it].match(actual[it], errors) }
    }

    override fun capture(arg: Any?) {
        val actual = arg.arrayElements() ?: return
        elements.forEachIndexed { index, element -> (element as? CapturingMatcher)?.capture(actual[index]) }
    }

    override fun toString(): String = elements.joinToString(prefix = "[", postfix = "]")
}

@PublishedApi
internal object Matchers {
    fun eq(value: Any?, inverse: Boolean = false): Matcher<Any?> =
        if (inverse) ValueMatcher("nonEq", value) { !deepEquals(it, value) } else EqMatcher(value)

    fun neq(value: Any?): Matcher<Any?> = eq(value, inverse = true)

    fun refEq(value: Any?, inverse: Boolean = false): Matcher<Any?> =
        if (inverse) ValueMatcher("refNonEq", value) { it !== value } else ValueMatcher("refEq", value) { it === value }

    fun nrefEq(value: Any?): Matcher<Any?> = refEq(value, inverse = true)

    fun isNull(inverse: Boolean = false): Matcher<Any?> =
        if (inverse) named("notNull()") { it != null } else named("null()") { it == null }

    fun cmpEq(value: Comparable<*>): Matcher<Any?> = comparing("cmpEq", value) { it == 0 }

    fun more(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        if (andEquals) comparing("moreAndEquals", value) { it >= 0 } else comparing("more", value) { it > 0 }

    fun less(value: Comparable<*>, andEquals: Boolean = false): Matcher<Any?> =
        if (andEquals) comparing("lessAndEquals", value) { it <= 0 } else comparing("less", value) { it < 0 }

    fun range(
        from: Comparable<*>,
        to: Comparable<*>,
        fromInclusive: Boolean = true,
        toInclusive: Boolean = true,
    ): Matcher<Any?> = and(more(from, fromInclusive), less(to, toInclusive))

    fun and(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("and", arrayOf(left, right)) { arg, errors ->
            left.match(arg, errors) && right.match(arg, errors)
        }

    fun or(left: Matcher<Any?>, right: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("or", arrayOf(left, right)) { arg, errors ->
            left.match(arg, errors) || right.match(arg, errors)
        }

    fun not(value: Matcher<Any?>): Matcher<Any?> =
        CompositeMatcher("not", arrayOf(value)) { arg, errors -> !value.match(arg, errors) }

    @Suppress("UNCHECKED_CAST")
    fun match(matcher: (Nothing) -> Boolean): Matcher<Any?> {
        val predicate = matcher as (Any?) -> Boolean
        return FunctionMatcher { it != null && predicate(it) }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> match(matcher: Matcher<T>): Matcher<Any?> = filtered(matcher as Matcher<Any?>) { it is T }

    @Suppress("UNCHECKED_CAST")
    fun match(matcher: Matcher<*>, kclass: KClass<*>): Matcher<Any?> =
        filtered(matcher as Matcher<Any?>) { kclass.isInstance(it) }

    @Suppress("UNCHECKED_CAST")
    fun matchNullable(matcher: (Nothing?) -> Boolean): Matcher<Any?> = FunctionMatcher(matcher as (Any?) -> Boolean)

    @Suppress("UNCHECKED_CAST")
    fun withArg(captureBlock: AssertScope.(Nothing) -> Unit): Matcher<Any?> =
        match(asserting(captureBlock as AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun withNullableArg(captureBlock: AssertScope.(Nothing?) -> Unit): Matcher<Any?> =
        matchNullable(asserting(captureBlock as AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun coMatch(matcher: suspend (Nothing) -> Boolean): Matcher<Any?> {
        val predicate = matcher as suspend (Any?) -> Boolean
        return match { arg: Any? -> complete("coMatch") { predicate(arg) } }
    }

    @Suppress("UNCHECKED_CAST")
    fun coMatchNullable(matcher: suspend (Nothing?) -> Boolean): Matcher<Any?> {
        val predicate = matcher as suspend (Any?) -> Boolean
        return matchNullable { arg: Any? -> complete("coMatchNullable") { predicate(arg) } }
    }

    @Suppress("UNCHECKED_CAST")
    fun coWithArg(captureBlock: suspend AssertScope.(Nothing) -> Unit): Matcher<Any?> =
        match(coAsserting("coWithArg", captureBlock as suspend AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun coWithNullableArg(captureBlock: suspend AssertScope.(Nothing?) -> Unit): Matcher<Any?> =
        matchNullable(coAsserting("coWithNullableArg", captureBlock as suspend AssertScope.(Any?) -> Unit))

    @Suppress("UNCHECKED_CAST")
    fun capture(lst: MutableList<*>): Matcher<Any?> =
        CaptureMatcher(slot = false) { if (it != null) (lst as MutableList<Any?>).add(it) }

    @Suppress("UNCHECKED_CAST")
    fun capture(lst: CapturingSlot<*>): Matcher<Any?> =
        CaptureMatcher(slot = true) { if (it != null) (lst as CapturingSlot<Any?>).captured = it }

    @Suppress("UNCHECKED_CAST")
    fun captureNullable(lst: MutableList<*>): Matcher<Any?> =
        CaptureMatcher(slot = false) { (lst as MutableList<Any?>).add(it) }

    @Suppress("UNCHECKED_CAST")
    fun captureNullable(lst: CapturingSlot<*>): Matcher<Any?> =
        CaptureMatcher(slot = true) { (lst as CapturingSlot<Any?>).captured = it }

    fun captureLambda(): Matcher<Any?> = LambdaMatcher

    fun captureCoroutine(): Matcher<Any?> = LambdaMatcher

    @Suppress("UNCHECKED_CAST")
    fun invoke(name: String, arguments: Array<out Any?>, invocation: (Nothing) -> Unit): Matcher<Any?> =
        InvokeMatcher(name, arguments, invocation as (Any?) -> Unit)

    @Suppress("UNCHECKED_CAST")
    fun coInvoke(name: String, arguments: Array<out Any?>, invocation: suspend (Nothing) -> Unit): Matcher<Any?> {
        val coInvocation = invocation as suspend (Any?) -> Unit
        return InvokeMatcher(name, arguments) { complete(name) { coInvocation(it) } }
    }

    fun array(elements: Array<Matcher<Any?>>): Matcher<Any?> = ArrayMatcher(elements)

    inline fun <reified T> ofType(): Matcher<Any?> = named("ofType(${T::class.simpleName})") { it is T }

    fun ofType(cls: KClass<*>): Matcher<Any?> = named("ofType(${cls.simpleName})") { cls.isInstance(it) }

    inline fun <reified T> typed(matcher: Matcher<Any?>): Matcher<Any?> = filtered(matcher) { it == null || it is T }

    fun filtered(matcher: Matcher<Any?>, accepts: (Any?) -> Boolean): Matcher<Any?> = FilteredMatcher(matcher, accepts)

    fun named(name: String, matches: (Any?) -> Boolean): Matcher<Any?> = NamedMatcher(name, matches)

    fun spread(array: Any): Array<Matcher<Any?>> {
        val elements = array.arrayElements()!!
        return Array(elements.size) { EqMatcher(elements[it]) }
    }

    inline fun <reified T : Any> anyVararg(): Matcher<Any?> =
        varargMatcher("anyVararg", all = true, { it == null || it is T }) { true }

    fun anyBooleanVararg(): Matcher<Any?> = anyVararg<Boolean>()

    fun anyByteVararg(): Matcher<Any?> = anyVararg<Byte>()

    fun anyCharVararg(): Matcher<Any?> = anyVararg<Char>()

    fun anyShortVararg(): Matcher<Any?> = anyVararg<Short>()

    fun anyIntVararg(): Matcher<Any?> = anyVararg<Int>()

    fun anyLongVararg(): Matcher<Any?> = anyVararg<Long>()

    fun anyFloatVararg(): Matcher<Any?> = anyVararg<Float>()

    fun anyDoubleVararg(): Matcher<Any?> = anyVararg<Double>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> varargAll(noinline matcher: VarargScope.(T) -> Boolean): Matcher<Any?> =
        varargMatcher("varargAll", all = true, { it is T }, matcher as VarargScope.(Any?) -> Boolean)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> varargAllNullable(noinline matcher: VarargScope.(T?) -> Boolean): Matcher<Any?> =
        varargMatcher("varargAll", all = true, { it == null || it is T }, matcher as VarargScope.(Any?) -> Boolean)

    fun varargAllBoolean(matcher: VarargScope.(Boolean) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllByte(matcher: VarargScope.(Byte) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllChar(matcher: VarargScope.(Char) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllShort(matcher: VarargScope.(Short) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllInt(matcher: VarargScope.(Int) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllLong(matcher: VarargScope.(Long) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllFloat(matcher: VarargScope.(Float) -> Boolean): Matcher<Any?> = varargAll(matcher)

    fun varargAllDouble(matcher: VarargScope.(Double) -> Boolean): Matcher<Any?> = varargAll(matcher)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> varargAny(noinline matcher: VarargScope.(T) -> Boolean): Matcher<Any?> =
        varargMatcher("varargAny", all = false, { it is T }, matcher as VarargScope.(Any?) -> Boolean)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> varargAnyNullable(noinline matcher: VarargScope.(T?) -> Boolean): Matcher<Any?> =
        varargMatcher("varargAny", all = false, { it == null || it is T }, matcher as VarargScope.(Any?) -> Boolean)

    fun varargAnyBoolean(matcher: VarargScope.(Boolean) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyByte(matcher: VarargScope.(Byte) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyChar(matcher: VarargScope.(Char) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyShort(matcher: VarargScope.(Short) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyInt(matcher: VarargScope.(Int) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyLong(matcher: VarargScope.(Long) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyFloat(matcher: VarargScope.(Float) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargAnyDouble(matcher: VarargScope.(Double) -> Boolean): Matcher<Any?> = varargAny(matcher)

    fun varargMatcher(
        name: String,
        all: Boolean,
        accepts: (Any?) -> Boolean,
        matcher: VarargScope.(Any?) -> Boolean,
    ): Matcher<Any?> = VarargMatcher(name, all, accepts, matcher, emptyArray(), emptyArray())

    fun varargOf(prefix: Array<Matcher<Any?>>, matcher: Matcher<Any?>, postfix: Array<Matcher<Any?>>): Matcher<Any?> {
        if (matcher !is VarargMatcher) {
            throw LokkiException(
                "$matcher cannot be spread into a vararg parameter: " +
                    "only anyVararg, varargAll and varargAny match a part of the array",
            )
        }
        return matcher.between(prefix, postfix)
    }
}

internal interface CapturingMatcher {
    fun capture(arg: Any?)
}

internal interface ReportingMatcher {
    fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean
}

internal fun Matcher<Any?>.match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
    if (this is ReportingMatcher) match(arg, errors) else match(arg)

internal fun Array<Matcher<Any?>>.match(arguments: Array<Any?>): Boolean {
    for (index in indices) {
        if (!this[index].match(arguments[index])) return false
    }
    return true
}

internal fun Array<Matcher<Any?>>.capture(arguments: Array<Any?>) {
    for (index in indices) {
        (this[index] as? CapturingMatcher)?.capture(arguments[index])
    }
}

internal fun equalTo(arguments: Array<Any?>): Array<Matcher<Any?>> = Array(arguments.size) { EqMatcher(arguments[it]) }

internal fun Array<Matcher<Any?>>.isEquivalentTo(other: Array<Matcher<Any?>>): Boolean =
    size == other.size && indices.all { this[it].isEquivalentTo(other[it]) }

private fun Matcher<Any?>.isEquivalentTo(other: Matcher<Any?>): Boolean {
    val left = equivalent()
    val right = other.equivalent()
    return when (left) {
        is EqMatcher -> right is EqMatcher && deepEquals(left.value, right.value)
        is ValueMatcher -> right is ValueMatcher && left.name == right.name && deepEquals(left.value, right.value)
        is NamedMatcher -> right is NamedMatcher && left.name == right.name
        is CompositeMatcher ->
            right is CompositeMatcher && left.name == right.name && left.operands.isEquivalentTo(right.operands)
        is ArrayMatcher -> right is ArrayMatcher && left.elements.isEquivalentTo(right.elements)
        is VarargMatcher -> right is VarargMatcher && left.name == right.name &&
            left.prefix.isEquivalentTo(right.prefix) && left.postfix.isEquivalentTo(right.postfix)
        else -> left === right
    }
}

private fun Matcher<Any?>.equivalent(): Matcher<Any?> =
    when (this) {
        is FilteredMatcher -> matcher.equivalent()
        is EqMatcher, is ValueMatcher, is NamedMatcher, is CompositeMatcher, is ArrayMatcher, is VarargMatcher -> this
        else -> AnyMatcher
    }

internal val Matcher<Any?>.capturesIntoSlot: Boolean
    get() = when (this) {
        is CaptureMatcher -> slot
        is FilteredMatcher -> matcher.capturesIntoSlot
        else -> false
    }

internal val Matcher<Any?>.capturesLambda: Boolean
    get() = this === LambdaMatcher || this is FilteredMatcher && matcher.capturesLambda

private object LambdaMatcher : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = arg != null

    override fun toString(): String = "slotCapture()"
}

private class InvokeMatcher(
    private val name: String,
    private val arguments: Array<out Any?>,
    private val invocation: (Any?) -> Unit,
) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean {
        if (arg != null) invocation(arg)
        return true
    }

    override fun toString(): String = arguments.joinToString(prefix = "$name(", postfix = ")") { it.toStr() }
}

private class NamedMatcher(val name: String, private val matches: (Any?) -> Boolean) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = matches(arg)

    override fun toString(): String = name
}

private class FunctionMatcher(private val predicate: (Any?) -> Boolean) : Matcher<Any?>, ReportingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
        try {
            predicate(arg)
        } catch (e: AssertionError) {
            errors?.add(e)
            false
        }

    override fun toString(): String = "matcher()"
}

private class ValueMatcher(
    val name: String,
    val value: Any?,
    private val matches: (Any?) -> Boolean,
) : Matcher<Any?> {
    override fun match(arg: Any?): Boolean = matches(arg)

    override fun toString(): String = "$name(${value.toStr()})"
}

private class CaptureMatcher(val slot: Boolean, private val capture: (Any?) -> Unit) : Matcher<Any?>, CapturingMatcher {
    override fun match(arg: Any?): Boolean = true

    override fun capture(arg: Any?) {
        capture.invoke(arg)
    }

    override fun toString(): String = if (slot) "slotCapture()" else "capture()"
}

private class FilteredMatcher(val matcher: Matcher<Any?>, private val accepts: (Any?) -> Boolean) :
    Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean =
        accepts(arg) && matcher.match(arg, errors)

    override fun capture(arg: Any?) {
        (matcher as? CapturingMatcher)?.capture(arg)
    }

    override fun toString(): String = matcher.toString()
}

private class CompositeMatcher(
    val name: String,
    val operands: Array<Matcher<Any?>>,
    private val matches: (Any?, MutableList<AssertionError>?) -> Boolean,
) : Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    override fun match(arg: Any?): Boolean = matches(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean = matches(arg, errors)

    override fun capture(arg: Any?) {
        operands.forEach { (it as? CapturingMatcher)?.capture(arg) }
    }

    override fun toString(): String = operands.joinToString(prefix = "$name(", postfix = ")")
}

private class VarargMatcher(
    val name: String,
    private val all: Boolean,
    private val accepts: (Any?) -> Boolean,
    private val matcher: VarargScope.(Any?) -> Boolean,
    val prefix: Array<Matcher<Any?>>,
    val postfix: Array<Matcher<Any?>>,
) : Matcher<Any?>, ReportingMatcher, CapturingMatcher {
    fun between(prefix: Array<Matcher<Any?>>, postfix: Array<Matcher<Any?>>): VarargMatcher =
        VarargMatcher(name, all, accepts, matcher, prefix, postfix)

    override fun match(arg: Any?): Boolean = match(arg, null)

    override fun match(arg: Any?, errors: MutableList<AssertionError>?): Boolean {
        val actual = arg.arrayElements() ?: return false
        val end = actual.size - postfix.size
        if (end < prefix.size) return false
        if (prefix.indices.any { !prefix[it].match(actual[it], errors) }) return false
        if (postfix.indices.any { !postfix[it].match(actual[end + it], errors) }) return false
        val central = prefix.size until end
        return if (all) {
            central.all { matches(it, actual, errors) }
        } else {
            central.any { matches(it, actual, errors) }
        }
    }

    private fun matches(position: Int, actual: List<Any?>, errors: MutableList<AssertionError>?): Boolean {
        val element = actual[position]
        if (!accepts(element)) return false
        return try {
            VarargScope(position, actual.size).matcher(element)
        } catch (e: AssertionError) {
            errors?.add(e)
            false
        }
    }

    override fun capture(arg: Any?) {
        val actual = arg.arrayElements() ?: return
        val end = actual.size - postfix.size
        if (end < prefix.size) return
        prefix.forEachIndexed { index, element -> (element as? CapturingMatcher)?.capture(actual[index]) }
        postfix.forEachIndexed { index, element -> (element as? CapturingMatcher)?.capture(actual[end + index]) }
    }

    override fun toString(): String = (prefix.map { it.toString() } + "*$name()" + postfix.map { it.toString() })
        .joinToString(prefix = "[", postfix = "]")
}

private fun asserting(block: AssertScope.(Any?) -> Unit): (Any?) -> Boolean = { arg ->
    AssertScope(arg).block(arg)
    true
}

private fun coAsserting(name: String, block: suspend AssertScope.(Any?) -> Unit): (Any?) -> Boolean = { arg ->
    complete(name) { AssertScope(arg).block(arg) }
    true
}

@Suppress("UNCHECKED_CAST")
private fun <T> complete(name: String, block: suspend () -> T): T {
    val result = block.startCoroutineUninterceptedOrReturn(Abandoned)
    if (result === COROUTINE_SUSPENDED) {
        throw LokkiException("$name suspended; a matcher must complete without suspension")
    }
    return result as T
}

@Suppress("UNCHECKED_CAST")
private inline fun comparing(name: String, value: Comparable<*>, crossinline matches: (Int) -> Boolean): Matcher<Any?> =
    ValueMatcher(name, value) { it != null && matches((it as Comparable<Any?>).compareTo(value)) }

private fun Any?.arrayElements(): List<Any?>? =
    when (this) {
        is Array<*> -> asList()
        is BooleanArray -> asList()
        is ByteArray -> asList()
        is CharArray -> asList()
        is ShortArray -> asList()
        is IntArray -> asList()
        is LongArray -> asList()
        is FloatArray -> asList()
        is DoubleArray -> asList()
        else -> null
    }

internal fun deepEquals(a: Any?, b: Any?): Boolean =
    a === b || when (a) {
        is Array<*> -> b is Array<*> && a.contentDeepEquals(b)
        is BooleanArray -> b is BooleanArray && a.contentEquals(b)
        is ByteArray -> b is ByteArray && a.contentEquals(b)
        is CharArray -> b is CharArray && a.contentEquals(b)
        is ShortArray -> b is ShortArray && a.contentEquals(b)
        is IntArray -> b is IntArray && a.contentEquals(b)
        is LongArray -> b is LongArray && a.contentEquals(b)
        is FloatArray -> b is FloatArray && a.contentEquals(b)
        is DoubleArray -> b is DoubleArray && a.contentEquals(b)
        else -> a == b
    }

internal fun Any?.toStr(): String =
    try {
        when (this) {
            null -> "null"
            is Array<*> -> contentDeepToString()
            is BooleanArray -> contentToString()
            is ByteArray -> contentToString()
            is CharArray -> contentToString()
            is ShortArray -> contentToString()
            is IntArray -> contentToString()
            is LongArray -> contentToString()
            is FloatArray -> contentToString()
            is DoubleArray -> contentToString()
            is KClass<*> -> simpleName ?: "<null name class>"
            is Function<*> -> "lambda {}"
            else -> toString()
        }
    } catch (e: Throwable) {
        "<error \"$e\">"
    }
