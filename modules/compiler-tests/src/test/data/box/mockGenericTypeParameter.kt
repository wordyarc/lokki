package sample

import io.lokki.every
import io.lokki.mock

interface Box<T> {
    fun get(): T
}

abstract class Holder<T> {
    abstract fun hold(): T
}

open class Repo<E : CharSequence> {
    open fun save(e: E): E = e
}

abstract class Contract<T> {
    val box = mock<Box<T>>()
    val holder = mock<Holder<T>>()
}

class Strings : Contract<String>()

inline fun <reified T> boxOf(): Box<T> = mock<Box<T>>()

fun <K, V> mapOfMocks(): Map<K, V> = mock<Map<K, V>>()

fun <T : CharSequence> repoOf(): Repo<T> = mock<Repo<T>>()

fun <T> nullableBox(): Box<T?> = mock<Box<T?>>()

fun box(): String {
    val contract = Strings()
    every { contract.box.get() } returns "c"
    every { contract.holder.hold() } returns "h"
    if (contract.box.get() != "c" || contract.holder.hold() != "h") return "Fail type parameter of a class"
    val boxClass = contract.box.javaClass
    if (boxClass.name != "sample.Box\$LokkiMock\$e0e6439c\$MockGenericTypeParameterKt") return "Fail name: ${boxClass.name}"
    if (boxClass.typeParameters.size != 1) return "Fail type parameters: ${boxClass.typeParameters.toList()}"

    val ints = boxOf<Int>()
    every { ints.get() } returns 1
    if (ints.get() != 1) return "Fail reified type parameter"
    if (ints.javaClass != boxClass) return "Fail: Box<T> of another declaration has another class"
    if (nullableBox<String>().javaClass == boxClass) return "Fail: Box<T?> shares the class of Box<T>"

    val map = mapOfMocks<String, Int>()
    every { map["k"] } returns 1
    if (map["k"] != 1) return "Fail two type parameters"
    if (map.javaClass != mapOfMocks<Int, Long>().javaClass || map.javaClass.typeParameters.size != 2) {
        return "Fail class of two type parameters"
    }

    val repo = repoOf<String>()
    every { repo.save(any()) } answers { callOriginal() + "!" }
    if (repo.save("s") != "s!") return "Fail bounded type parameter: ${repo.save("s")}"

    fun <T> local(): List<T> = mock<List<T>>()
    val strings = local<String>()
    every { strings.size } returns 3
    if (strings.size != 3) return "Fail type parameter of a local function"
    return "OK"
}
