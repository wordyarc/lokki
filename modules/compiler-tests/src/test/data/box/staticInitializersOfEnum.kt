// CHECK_BYTECODE_TEXT
package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock

interface Repo {
    fun find(id: Int): String
}

enum class WithArgument(val repo: Repo) {
    ENTRY(mock<Repo> { every { find(1) } returns "argument" }),
}

enum class WithDefault(val repo: Repo = mock<Repo> { every { find(1) } returns "default" }) {
    ENTRY,
}

enum class WithProperty {
    ENTRY;

    val repo = mock<Repo> { every { find(1) } returns "property" }
}

enum class WithInit {
    ENTRY;

    val repo = mock<Repo>()

    init {
        every { repo.find(1) } returns "init"
    }
}

enum class WithBody {
    ENTRY {
        override val repo = mock<Repo> { every { find(1) } returns "body" }
    };

    abstract val repo: Repo
}

enum class WithoutMocks(val number: Int) {
    ENTRY(42),
}

private fun Repo.answer(id: Int): String =
    try {
        find(id)
    } catch (e: LokkiException) {
        "no answer"
    }

private fun repos(): List<Repo> =
    listOf(
        WithArgument.ENTRY.repo,
        WithDefault.ENTRY.repo,
        WithProperty.ENTRY.repo,
        WithInit.ENTRY.repo,
        WithBody.ENTRY.repo,
    )

fun box(): String {
    val configured = listOf("argument", "default", "property", "init", "body")
    val inScope = lokkiScope {
        val repos = repos()
        for (repo in repos) every { repo.find(2) } returns "test"
        repos.map { it.answer(1) } + repos.map { it.answer(2) }
    }
    if (inScope != configured + List(5) { "test" }) return "in scope: $inScope"

    val first = repos().map { it.answer(1) }
    if (first != configured) return "after scope: $first"
    val second = repos().map { it.answer(2) }
    if (second != List(5) { "no answer" }) return "test stubs after scope: $second"
    if (WithoutMocks.ENTRY.number != 42) return "number: ${WithoutMocks.ENTRY.number}"
    return "OK"
}

// 6 StaticInitializer.enter
