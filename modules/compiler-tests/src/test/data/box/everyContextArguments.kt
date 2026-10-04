package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

class Tenant(val name: String)

interface Repo {
    context(tenant: Tenant)
    fun load(id: Int): String

    context(tenant: Tenant)
    val size: Int
}

private fun failure(block: () -> Unit): String? =
    try {
        block()
        null
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    val repo = mock<Repo>()
    val first = Tenant("first")
    val second = Tenant("second")

    with(first) { every { repo.load(any()) } returns "of first" }
    if (with(first) { repo.load(1) } != "of first") return "Fail: context of with"
    if (failure { with(second) { repo.load(1) } }?.startsWith("no answer found") != true) {
        return "Fail: another context of with"
    }

    context(second) { every { repo.load(2) } returns "of second" }
    if (context(second) { repo.load(2) } != "of second") return "Fail: context of context"
    if (context(first) { repo.load(2) } != "of first") return "Fail: stub of the first context"

    with(first) { every { repo.size } returns 3 }
    if (with(first) { repo.size } != 3) return "Fail: context of a property"
    if (failure { with(second) { repo.size } }?.startsWith("no answer found") != true) {
        return "Fail: another context of a property"
    }

    return "OK"
}
