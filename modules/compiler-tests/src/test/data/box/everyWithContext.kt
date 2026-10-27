package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.justRun
import io.lokki.mock
import io.lokki.verify
import io.lokki.withContext

class Tenant(val name: String)

class Locale(val tag: String)

interface Repo {
    context(tenant: Tenant)
    fun load(id: Int): String

    context(tenant: Tenant, locale: Locale)
    fun label(id: Int, suffix: String = "!"): String

    context(tenant: Tenant)
    val size: Int

    context(tenant: Tenant)
    fun save(id: Int)
}

private fun failure(block: () -> Any?): String =
    try {
        block()
        "no exception"
    } catch (e: LokkiException) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    } catch (e: AssertionError) {
        e.message!!.replace(Regex("""\(#\d+\)"""), "")
    }

fun box(): String {
    val repo = mock<Repo>()
    val first = Tenant("first")
    val second = Tenant("second")
    val english = Locale("en")

    every { withContext<Tenant> { repo.load(any()) } } returns "any tenant"
    if (with(first) { repo.load(1) } != "any tenant") return "Fail: first tenant"
    if (with(second) { repo.load(2) } != "any tenant") return "Fail: second tenant"

    every { withContext<Tenant> { repo.load(3) } } returns "three"
    if (with(second) { repo.load(3) } != "three") return "Fail: value argument"

    every { withContext<Tenant, Locale> { repo.label(1) } } returns "label"
    if (with(first) { with(english) { repo.label(1) } } != "label") return "Fail: two contexts"
    val strict = failure { with(first) { with(english) { repo.label(2) } } }
    if ("Repo.label(any(), any(), eq(1), eq(!))" !in strict) return "Fail: description: $strict"

    every { withContext<Tenant, Locale> { repo.label(suffix = "?", id = more(5)) } } returns "named"
    if (with(second) { with(english) { repo.label(6, "?") } } != "named") return "Fail: named arguments"

    every { withContext<Tenant> { repo.size } } returns 7
    if (with(first) { repo.size } != 7) return "Fail: property"

    justRun { withContext<Tenant> { repo.save(any()) } }
    with(first) { repo.save(1) }

    verify { withContext<Tenant> { repo.save(1) } }
    verify(exactly = 3) { withContext<Tenant> { repo.load(any()) } }
    val verification = failure { verify { withContext<Tenant> { repo.save(2) } } }
    if ("Repo.save(any(), eq(2))" !in verification) return "Fail: verification failure: $verification"

    excludeRecords { withContext<Tenant> { repo.load(any()) } }
    verify(exactly = 0) { withContext<Tenant> { repo.load(any()) } }

    return "OK"
}
