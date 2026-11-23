package sample

import io.lokki.every
import io.lokki.isMock
import io.lokki.mockClass

interface Repo {
    fun find(id: Int): String
}

open class Box<T>(val value: T) {
    open fun size(): Int = 1
}

private fun returnsFromBlock(): String {
    mockClass(Repo::class) { return "returned" }
    return "Fail"
}

fun box(): String {
    if (returnsFromBlock() != "returned") return "Fail non-local return"
    val repo = mockClass(Repo::class)
    every { repo.find(1) } returns "one"
    if (repo.find(1) != "one" || !isMock(repo)) return "Fail literal"
    val named = mockClass(Repo::class, name = "named", relaxed = true)
    if (named.find(1) != "" || "named" !in named.toString()) return "Fail arguments"
    val configured = mockClass(Repo::class) { every { find(2) } returns "two" }
    if (configured.find(2) != "two") return "Fail block"
    val generic = mockClass(Box::class, relaxed = true)
    return if (generic.size() == 0) "OK" else "Fail generic"
}
