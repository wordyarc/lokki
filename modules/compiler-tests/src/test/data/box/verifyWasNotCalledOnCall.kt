package sample

import io.lokki.Called
import io.lokki.every
import io.lokki.mock
import io.lokki.verify

interface Repo {
    val size: Int

    fun find(id: Int): String

    fun drop(id: Int)

    fun child(): Repo
}

open class Holder(open val repo: Repo)

private inline fun fails(block: () -> Unit): Boolean =
    try {
        block()
        false
    } catch (_: AssertionError) {
        true
    }

fun box(): String {
    val repo = mock<Repo>(relaxUnitFun = true)
    every { repo.find(any()) } returns "x"
    verify { repo.find(1) wasNot Called }
    verify { repo.find(any()) wasNot Called }
    verify { repo.drop(1) wasNot Called }
    verify { repo.size wasNot Called }
    repo.find(1)
    repo.drop(1)
    if (!fails { verify { repo.find(1) wasNot Called } }) return "Fail called value"
    if (!fails { verify { repo.find(any()) wasNot Called } }) return "Fail called matcher"
    if (!fails { verify { repo.drop(1) wasNot Called } }) return "Fail called Unit"
    verify { repo.find(2) wasNot Called }
    verify(inverse = true) { repo.find(1) wasNot Called }
    verify {
        repo.find(1)
        repo.find(2) wasNot Called
    }

    val chained = mock<Repo>()
    every { chained.child().find(1) } returns "c"
    verify { chained.child().find(2) wasNot Called }
    chained.child().find(1)
    if (!fails { verify { chained.child().find(1) wasNot Called } }) return "Fail chained"

    val inner = mock<Repo>(relaxUnitFun = true)
    val holder = Holder(inner)
    verify { holder.repo wasNot Called }
    inner.drop(0)
    if (!fails { verify { holder.repo wasNot Called } }) return "Fail property of a real object"
    return "OK"
}
