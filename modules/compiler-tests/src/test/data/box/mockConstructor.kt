// MODULE: main
// MOCKABLE: sample.**
// FILE: main.kt
package sample

class Example {
    val property: Int = 1
}

interface TokenProvider {
    fun token(): String
}

data class WithRef(private val action: String, private val tokenProvider: TokenProvider) {
    fun op() = "$action:${tokenProvider.token()}"
}

data class Cls(private val x: Int = 0) {
    constructor(x: String) : this(x.toInt())

    fun op(a: Int, b: Int) = a + b + x

    fun opList(a: Int, b: Int) = listOf(a, b)

    fun chainOp(a: Int, b: Int) = Cls(a + b + x)

    fun viaSelf(a: Int) = op(a, a)
}

class Defaults(val a: Int, val b: Int = a * 2, val tail: String = "t$b") {
    fun sum() = a + b
}

class Varargs(vararg val items: String) {
    fun size() = items.size
}

class Generic<T>(val value: T) {
    fun get(): T = value
}

class Outer {
    class Nested(val n: Int) {
        fun twice() = n * 2
    }
}

private val secret = listOf("private")

private fun hidden() = "hidden"

class PrivateDefaults(val a: List<String> = secret, val b: String = hidden(), val c: Int = SEED) {
    fun op() = "$a$b$c"

    private companion object {
        val SEED = 7
    }
}

@JvmInline
value class Id(val raw: Int)

class WithValue(val id: Id, val fallback: Id = Id(id.raw + 1), val label: String? = null) {
    fun raw() = id.raw + fallback.raw
}

class Bounded<T : Comparable<T>>(val first: T, vararg val rest: T) {
    fun max(): T = (listOf(first) + rest).maxOrNull()!!
}

class WithLambda(val transform: (Int) -> Int = { it + 1 }) {
    fun apply(a: Int) = transform(a)
}

fun newPrivateDefaults() = PrivateDefaults()

fun newWithValue() = WithValue(Id(3))

fun newBounded() = Bounded(1, 5, 3)

fun newWithLambda() = WithLambda()

fun viaReference() = listOf(1).map(::Cls).single()

fun genericViaReference() = listOf("real").map(::Generic).single()

fun varargsViaReference(): Varargs {
    val create: (Array<String>) -> Varargs = ::Varargs
    return create(arrayOf("a", "b", "c"))
}

fun referenceName(): String {
    val create: kotlin.reflect.KFunction1<Int, Cls> = ::Cls
    return create.name
}

fun newCls(x: Int) = Cls(x)

fun newDefaults() = Defaults(1)

fun newVarargs() = Varargs("a", "b")

fun newGeneric() = Generic("real")

fun newNested() = Outer.Nested(4)

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.LokkiException
import io.lokki.clearConstructorMock
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.isMock
import io.lokki.lokkiScope
import io.lokki.mock
import io.lokki.mockConstructor
import io.lokki.unmockAll
import io.lokki.unmockConstructor
import io.lokki.verify
import sample.Cls
import sample.Defaults
import sample.Example
import sample.Bounded
import sample.Generic
import sample.Id
import sample.Outer
import sample.PrivateDefaults
import sample.TokenProvider
import sample.Varargs
import sample.WithLambda
import sample.WithRef
import sample.WithValue
import sample.genericViaReference
import sample.newBounded
import sample.newCls
import sample.newDefaults
import sample.newGeneric
import sample.newNested
import sample.newPrivateDefaults
import sample.newVarargs
import sample.newWithLambda
import sample.newWithValue
import sample.referenceName
import sample.varargsViaReference
import sample.viaReference

private inline fun fails(block: () -> Unit): Boolean =
    try {
        block()
        false
    } catch (_: AssertionError) {
        true
    } catch (_: LokkiException) {
        true
    }

private fun check(name: String, block: () -> String?): String? = lokkiScope { block()?.let { "$name: $it" } }

fun box(): String {
    val failures = listOf(
        check("simple") {
            mockConstructor(Example::class)
            every { anyConstructed<Example>().property } returns 0
            if (Example().property != 0) "stub" else null
        },
        check("unmockAll") {
            mockConstructor(Example::class)
            mockConstructor(Example::class)
            every { anyConstructed<Example>().property } returns 0
            if (Example().property != 0) return@check "stub"
            unmockAll()
            if (Example().property != 1) return@check "unmocked"
            if (!fails { every { anyConstructed<Example>().property } returns 0 }) return@check "not mocked"
            null
        },
        check("call from main") {
            mockConstructor(Cls::class)
            every { anyConstructed<Cls>().op(1, 2) } returns 4
            if (newCls(0).op(1, 2) != 4) return@check "stub"
            verify { anyConstructed<Cls>().op(1, 2) }
            null
        },
        check("real constructor and original") {
            mockConstructor(Cls::class)
            every { anyConstructed<Cls>().op(1, 1) } returns 100
            val instance = newCls(5)
            if (!isMock(instance)) return@check "not a mock"
            if (instance.op(2, 2) != 9) return@check "unstubbed call must use constructor state"
            if (instance.viaSelf(1) != 100) return@check "self call must see the stub"
            null
        },
        check("verify without every") {
            mockConstructor(Cls::class)
            newCls(1).op(1, 2)
            newCls(2).op(1, 2)
            verify(exactly = 2) { anyConstructed<Cls>().op(1, 2) }
            null
        },
        check("stub after the instance") {
            mockConstructor(Cls::class)
            val instance = newCls(1)
            every { anyConstructed<Cls>().op(1, 2) } returns 42
            if (instance.op(1, 2) != 42) "stub" else null
        },
        check("clear") {
            mockConstructor(Cls::class)
            every { anyConstructed<Cls>().op(1, 2) } returns 4
            if (Cls().op(1, 2) != 4) return@check "stub"
            verify { anyConstructed<Cls>().op(1, 2) }
            clearConstructorMock(Cls::class)
            verify(exactly = 0) { anyConstructed<Cls>() }
            if (Cls().op(1, 2) != 3) return@check "cleared"
            verify { anyConstructed<Cls>().op(1, 2) }
            null
        },
        check("instance after unmock") {
            mockConstructor(Cls::class)
            every { anyConstructed<Cls>().op(1, 2) } returns 4
            val instance = Cls()
            unmockConstructor(Cls::class)
            if (instance.op(1, 2) != 3) "instance keeps the stub" else null
        },
        check("fake constructor") {
            mockConstructor(Cls::class)
            every { Cls(5).op(1, 2) } returns 4
            if (Cls(5).op(1, 2) != 4) return@check "same arguments"
            if (Cls(6).op(1, 2) != 9) return@check "other arguments"
            verify { Cls(5).op(1, 2) }
            null
        },
        check("constructedWith") {
            mockConstructor(Cls::class)
            every { constructedWith<Cls>(ofType<String>()).op(any(), any()) } returns 23
            every { constructedWith<Cls>(eq(6)).op(any(), any()) } returns 55
            every { constructedWith<Cls>(ofType<Int>()).op(any(), any()) } returns 35
            if (Cls("5").op(1, 2) != 23) return@check "string"
            if (Cls(5).op(1, 2) != 35) return@check "int"
            if (Cls(6).op(1, 2) != 55) return@check "eq"
            verify { constructedWith<Cls>(eq(6)).op(1, 2) }
            null
        },
        check("constructedWith values") {
            mockConstructor(WithRef::class)
            val first = mock<TokenProvider>()
            val second = mock<TokenProvider>()
            every { constructedWith<WithRef>("ACTION", refEq(first)).op() } returns "a"
            every { constructedWith<WithRef>("ACTION", refEq(second)).op() } returns "b"
            if (WithRef("ACTION", first).op() != "a" || WithRef("ACTION", second).op() != "b") return@check "ref"
            verify { constructedWith<WithRef>("ACTION", refEq(first)).op() }
            null
        },
        check("constructedWith after the instance") {
            mockConstructor(Cls::class)
            val instance = Cls(5)
            instance.op(1, 2)
            verify { constructedWith<Cls>(ofType<Int>()).op(1, 2) }
            verify(exactly = 0) { constructedWith<Cls>(ofType<String>()).op(1, 2) }
            every { constructedWith<Cls>(ofType<Int>()).op(3, 4) } returns 70
            if (instance.op(3, 4) != 70) return@check "stub registered after the instance"
            if (Cls(5).op(3, 4) != 70) return@check "next instance with the same arguments"
            null
        },
        check("exact stub wins over constructedWith") {
            mockConstructor(Cls::class)
            every { constructedWith<Cls>(ofType<Int>()).op(1, 2) } returns 1
            every { Cls(5).op(1, 2) } returns 2
            if (Cls(5).op(1, 2) != 2) return@check "exact"
            if (Cls(6).op(1, 2) != 1) return@check "matcher"
            null
        },
        check("constructedWith without arguments") {
            mockConstructor(Cls::class)
            every { constructedWith<Cls>().op(1, 3) } returns 33
            if (Cls().op(1, 3) != 33) return@check "stub"
            verify { constructedWith<Cls>().op(1, 3) }
            null
        },
        check("chain") {
            mockConstructor(Cls::class)
            every { Cls().chainOp(1, 2).chainOp(3, 4).op(5, 6) } returns 7
            if (Cls().chainOp(1, 2).chainOp(3, 4).op(5, 6) != 7) return@check "stub"
            verify { Cls().chainOp(1, 2).chainOp(3, 4).op(5, 6) }
            clearConstructorMock(Cls::class)
            if (Cls().chainOp(1, 2).chainOp(3, 4).op(5, 6) != 21) return@check "cleared"
            null
        },
        check("omitted default arguments") {
            mockConstructor(Defaults::class)
            every { constructedWith<Defaults>(1, 2, "t2").sum() } returns 50
            val created = newDefaults()
            if (created.b != 2 || created.tail != "t2") return@check "defaults: ${created.b} ${created.tail}"
            if (created.sum() != 50) return@check "matched by default values"
            null
        },
        check("varargs") {
            mockConstructor(Varargs::class)
            every { anyConstructed<Varargs>().size() } returns 9
            if (newVarargs().size() != 9) return@check "stub"
            if (newVarargs().items.toList() != listOf("a", "b")) return@check "state"
            null
        },
        check("generic") {
            mockConstructor(Generic::class)
            val created = newGeneric()
            if (created.get() != "real") return@check "original"
            null
        },
        check("nested") {
            mockConstructor(Outer.Nested::class)
            every { anyConstructed<Outer.Nested>().twice() } returns 1
            if (newNested().twice() != 1) "stub" else null
        },
        check("defaults reading private declarations") {
            mockConstructor(PrivateDefaults::class)
            if (newPrivateDefaults().op() != "[private]hidden7") return@check newPrivateDefaults().op()
            every { constructedWith<PrivateDefaults>(any(), "hidden", 7).op() } returns "stub"
            if (newPrivateDefaults().op() != "stub") "matched by private defaults" else null
        },
        check("value class parameters") {
            mockConstructor(WithValue::class)
            if (newWithValue().raw() != 7) return@check "original"
            every { constructedWith<WithValue>(Id(3), Id(4), isNull()).raw() } returns 1
            if (newWithValue().raw() != 1) "matched by value class" else null
        },
        check("bounded generic vararg") {
            mockConstructor(Bounded::class)
            if (newBounded().max() != 5) "original" else null
        },
        check("lambda default") {
            mockConstructor(WithLambda::class)
            if (newWithLambda().apply(1) != 2) "original" else null
        },
        check("constructor references") {
            mockConstructor(Cls::class, Generic::class, Varargs::class)
            every { anyConstructed<Cls>().op(1, 2) } returns 9
            if (viaReference().op(1, 2) != 9) return@check "reference"
            if (!isMock(genericViaReference()) || genericViaReference().get() != "real") return@check "generic"
            every { anyConstructed<Varargs>().size() } returns 8
            if (varargsViaReference().size() != 8) return@check "vararg"
            if (varargsViaReference().items.size != 3) return@check "vararg state"
            if (referenceName() != "<init>") return@check "name: ${referenceName()}"
            null
        },
        check("block form") {
            var inside = 0
            mockConstructor(Cls::class) {
                every { anyConstructed<Cls>().op(1, 2) } returns 8
                inside = Cls().op(1, 2)
            }
            if (inside != 8 || Cls().op(1, 2) != 3) "block" else null
        },
        check("confirmVerified") {
            mockConstructor(Cls::class)
            every { anyConstructed<Cls>().op(1, 2) } returns 4
            Cls().op(1, 2)
            if (!fails { confirmVerified() }) return@check "unverified call passed"
            verify { anyConstructed<Cls>().op(1, 2) }
            confirmVerified()
            null
        },
    ).filterNotNull()
    return failures.firstOrNull()?.let { "Fail $it" } ?: if (Cls().op(1, 2) == 3) "OK" else "Fail scope leak"
}
