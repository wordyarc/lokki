package sample

import io.lokki.Called
import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.confirmVerified
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.just
import io.lokki.mock
import io.lokki.verify

interface Repo {
    fun find(id: Int, includeDeleted: Boolean = false): String
    fun save(name: String)
    fun count(): Int
}

class Plain : Repo {
    override fun find(id: Int, includeDeleted: Boolean): String = ""
    override fun save(name: String) {}
    override fun count(): Int = 0
}

fun box(): String {
    val repo = mock<Repo>()
    every { repo.find(any(), any()) } returns "found"
    every { repo.save(any()) } just Runs
    every { repo.count() } returns 0

    repo.find(1)
    repo.find(2)
    repo.count()
    excludeRecords { repo.find(1) }
    verify(exactly = 0) { repo.find(1) }
    verify(exactly = 1) { repo.find(2) }
    repo.find(1)
    verify(exactly = 0) { repo.find(1) }

    excludeRecords(current = false) { repo.count() }
    verify(exactly = 1) { repo.count() }
    repo.count()
    verify(exactly = 1) { repo.count() }

    excludeRecords {
        repo.find(more(5))
        repo.save(any())
    }
    repo.find(6)
    repo.find(6, includeDeleted = true)
    repo.save("a")
    verify(exactly = 0) { repo.find(6) }
    verify(exactly = 1) { repo.find(6, true) }
    verify(exactly = 0) { repo.save(any()) }
    confirmVerified(repo)

    var evaluations = 0
    fun current(): Boolean {
        evaluations++
        return false
    }
    excludeRecords(current()) {
        repo.find(7)
        repo.find(8)
    }
    if (evaluations != 1) return "Fail: current evaluated $evaluations times"
    excludeRecords(excludeBlock = { repo.find(9) }, current = true)

    val counter = mock<Repo>()
    every { counter.count() } returns 10
    excludeRecords { repo.find(counter.count()) }
    verify { counter wasNot Called }
    repo.find(7)
    repo.find(8)
    repo.find(9)
    repo.find(10)
    confirmVerified(repo)

    val quiet = mock<Repo>()
    every { quiet.count() } returns 0
    excludeRecords { quiet.count() }
    quiet.count()
    verify { quiet wasNot Called }

    val plain: Repo = Plain()
    val notMock = runCatching { excludeRecords { plain.find(1) } }.exceptionOrNull()
    if (notMock !is LokkiException || notMock.message != "$plain is not a lokki mock") return "Fail: $notMock"

    return "OK"
}
