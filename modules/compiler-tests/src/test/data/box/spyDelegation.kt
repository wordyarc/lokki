// WITH_STDLIB
package sample

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.spy
import io.lokki.verify
import java.io.Closeable
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

interface Repo {
    fun find(id: Int): String

    fun all(): List<String> = listOf(find(1))
}

class RealRepo : Repo {
    override fun find(id: Int): String = "real $id"
}

fun interface UseCase {
    suspend fun run(n: Int): Int
}

open class Settings {
    open val theme: String
        get() = "dark"

    open fun size(): Int = 1

    open fun describe(): String = "$theme ${size()}"
}

fun <T> run(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return outcome!!.getOrThrow()
}

fun box(): String {
    val real = RealRepo()
    val repo = spy<Repo>(real)
    if (repo.javaClass.name != "sample.Repo\$LokkiSpy\$1ddfef61\$SpyDelegationKt") return "Fail name: ${repo.javaClass.name}"
    if (repo.find(2) != "real 2") return "Fail forwarding: ${repo.find(2)}"
    every { repo.find(1) } returns "stubbed"
    if (repo.find(1) != "stubbed") return "Fail stub"
    if (repo.all() != listOf("real 1")) return "Fail call inside the instance: ${repo.all()}"
    verify(exactly = 2) { repo.find(any()) }
    verify { repo.all() }

    if (repo === real || repo != repo || repo == real) return "Fail identity"
    if (repo.hashCode() != System.identityHashCode(repo)) return "Fail hashCode"
    if (!repo.toString().startsWith("Repo(#")) return "Fail toString: $repo"

    val existing: Repo = real
    if (spy(existing).find(3) != "real 3") return "Fail existing instance"

    val closeable = spy<Repo>(real, moreInterfaces = arrayOf(Closeable::class))
    (closeable as Closeable).close()
    verify { (closeable as Closeable).close() }

    val useCase = spy(UseCase { it * 2 })
    if (run { useCase.run(3) } != 6) return "Fail suspend forwarding"
    coEvery { useCase.run(4) } returns 100
    if (run { useCase.run(4) } != 100) return "Fail suspend stub"
    coVerify(exactly = 2) { useCase.run(any()) }

    val function = spy<(Int) -> Int>({ it + 1 })
    if (function(1) != 2) return "Fail lambda"

    val list = spy<List<String>>(listOf("a", "b"))
    if (list.size != 2 || list[1] != "b" || "a" !in list) return "Fail list"
    every { list.size } returns 5
    if (list.size != 5) return "Fail list stub"

    val instance = Settings()
    val settings = spy(instance)
    if (settings.javaClass.superclass != Settings::class.java) return "Fail class delegation"
    val delegate = settings.javaClass.getDeclaredField("lokki\$delegate")
    if (Modifier.isFinal(delegate.modifiers) || !delegate.isSynthetic) return "Fail delegate field"
    if (settings.theme != "dark" || settings.size() != 1) return "Fail class forwarding"
    every { settings.size() } returns 3
    if (settings.describe() != "dark 1") return "Fail call inside the class instance: ${settings.describe()}"
    if (settings.size() != 3) return "Fail class stub"

    return "OK"
}
