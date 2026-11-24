@file:OptIn(InternalLokkiApi::class)

package sample

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.internal.ExternalMocks
import io.lokki.internal.LokkiIntrinsic
import io.lokki.isMock
import io.lokki.verify
import java.io.Closeable
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_MOCK)
annotation class Bean(
    val relaxed: Boolean = false,
    val relaxUnitFun: Boolean = false,
    val extraInterfaces: Array<KClass<*>> = [],
)

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@LokkiIntrinsic(LokkiIntrinsic.Kind.EXTERNAL_SPY)
annotation class SpyBean

interface Repo {
    fun find(id: Int): String
}

class RealRepo : Repo {
    override fun find(id: Int): String = "real $id"
}

class Holder {
    @Bean
    lateinit var repo: Repo

    @Bean(relaxed = true, extraInterfaces = [Closeable::class])
    lateinit var relaxed: Repo

    @SpyBean
    lateinit var spied: Repo
}

private fun create(field: String, name: String, instance: Any? = null): Any {
    val declared = Holder::class.java.getDeclaredField(field)
    return if (instance == null) ExternalMocks.mock(declared, name) else ExternalMocks.spy(declared, name, instance)
}

private fun failure(block: () -> Any): String? =
    try {
        block()
        null
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    val repo = create("repo", "repoBean") as Repo
    if (!isMock(repo) || !repo.toString().startsWith("Repo(repoBean")) return "Fail: mock $repo"
    every { repo.find(1) } returns "stubbed"
    if (repo.find(1) != "stubbed") return "Fail: stub"

    val relaxed = create("relaxed", "relaxedBean")
    if ((relaxed as Repo).find(2) != "") return "Fail: relaxed"
    if (relaxed !is Closeable) return "Fail: extra interface"

    val spied = create("spied", "spiedBean", RealRepo()) as Repo
    if (spied.find(3) != "real 3") return "Fail: spy forwards"
    every { spied.find(4) } returns "stubbed spy"
    if (spied.find(4) != "stubbed spy") return "Fail: spy stub"
    verify { spied.find(3) }

    if (failure { create("spied", "noBean") } != "sample.Holder.spied is a spy of an existing object, but no object was passed") {
        return "Fail: spy without an object"
    }
    if (failure { create("repo", "withBean", RealRepo()) } != "sample.Holder.repo is a mock, not a spy: it takes no object to wrap") {
        return "Fail: mock with an object"
    }
    return "OK"
}
