// CHECK_BYTECODE_TEXT
// FILE: fixtures.kt
package sample

import io.lokki.every
import io.lokki.mock

interface Repo {
    fun find(id: Int): String
}

fun fixtureRepo(): Repo = mock<Repo> { every { find(1) } returns "fixture" }

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock

class WithCompanion {
    companion object {
        val repo = mock<Repo> { every { find(1) } returns "companion" }
    }
}

object WithInit {
    const val ANSWER = "init"

    val repo = mock<Repo>()

    init {
        every { repo.find(1) } returns ANSWER
    }
}

val topLevel = mock<Repo> { every { find(1) } returns "top-level" }

val lazyRepo by lazy { mock<Repo> { every { find(1) } returns "lazy" } }

class WithHelper {
    companion object {
        val repo = fixtureRepo()
    }
}

class Instance {
    val repo = mock<Repo> { every { find(1) } returns "instance" }
}

val number = 42

private fun Repo.answer(id: Int): String =
    try {
        find(id)
    } catch (e: LokkiException) {
        "no answer"
    }

fun box(): String {
    lateinit var instance: Instance
    val inScope = lokkiScope {
        instance = Instance()
        val repos = listOf(WithCompanion.repo, WithInit.repo, topLevel, lazyRepo, WithHelper.repo, instance.repo)
        for (repo in repos) every { repo.find(2) } returns "test"
        repos.map { it.answer(1) } + repos.map { it.answer(2) }
    }
    if (inScope != listOf("companion", "init", "top-level", "lazy", "fixture", "instance") + List(6) { "test" }) {
        return "in scope: $inScope"
    }

    val repos = listOf(WithCompanion.repo, WithInit.repo, topLevel, lazyRepo, WithHelper.repo, instance.repo)
    val first = repos.map { it.answer(1) }
    if (first != listOf("companion", "init", "top-level", "lazy", "fixture", "no answer")) return "after scope: $first"
    val second = repos.map { it.answer(2) }
    if (second != List(6) { "no answer" }) return "test stubs after scope: $second"
    if (number != 42) return "number: $number"
    return "OK"
}

// 7 StaticInitializer.enter
// 14 StaticInitializer.exit
