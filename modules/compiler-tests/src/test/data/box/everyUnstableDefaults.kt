package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

class Token(val value: Int)

interface Repo {
    fun defaultLimit(): Int
    fun limit(id: Int, limit: Int = defaultLimit()): String
    fun shift(a: Int, b: Int = a + 1): String
    fun flag(a: Boolean, b: Boolean = a): String
    fun wide(a: Long, b: Long = a): String
    fun letter(a: Char, b: Char = a): String
    fun ratio(a: Double, b: Double = a): String
    fun stamp(id: Int, token: Token = Token(1)): String
}

private fun failure(block: () -> Unit): String =
    try {
        block()
        "registered"
    } catch (e: LokkiException) {
        "${e.message} / ${e.cause?.message}"
    }

private fun unstable(name: String): String =
    "default value of '$name' is unstable, has no equals, or depends on a matcher argument; " +
        "pass it explicitly or use any() / null"

fun box(): String {
    val repo = mock<Repo>()

    if (failure { every { repo.shift(any()) } returns "a" } != unstable("b")) return "Fail: default of a matcher argument"
    if (failure { every { repo.flag(any()) } returns "a" } != unstable("b")) return "Fail: Boolean placeholder"
    if (failure { every { repo.wide(any()) } returns "a" } != unstable("b")) return "Fail: Long placeholder"
    if (failure { every { repo.letter(any()) } returns "a" } != unstable("b")) return "Fail: Char placeholder"
    if (failure { every { repo.ratio(any()) } returns "a" } != unstable("b")) return "Fail: Double placeholder"
    if (failure { every { repo.stamp(1) } returns "a" } != unstable("token")) return "Fail: default without equals"

    every { repo.shift(1) } returns "shifted"
    if (repo.shift(1) != "shifted" || repo.shift(1, 2) != "shifted") return "Fail: default of a value argument"

    val unstubbed = failure { every { repo.limit(any()) } returns "a" }
    val expected = "default values of Repo.limit cannot be evaluated; pass them explicitly or use any() / " +
        "no answer found for Repo.defaultLimit"
    if (unstubbed != expected) return "Fail: default that fails: $unstubbed"

    every { repo.defaultLimit() } returnsMany listOf(10, 20)
    every { repo.limit(any()) } returns "limited"
    if (repo.limit(1, 10) != "limited") return "Fail: default of a stub with many answers"
    if (List(2) { repo.defaultLimit() } != listOf(10, 20)) return "Fail: registration moved the answers"

    return "OK"
}
