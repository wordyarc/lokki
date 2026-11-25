package io.lokki.benchmarks

interface Repository {
    fun find(id: Int): String

    fun findByName(name: String): String
}

open class Ledger(private val owner: String) {
    open fun balance(id: Int): Long = id.toLong() + owner.length
}

class FakeRepository : Repository {
    override fun find(id: Int): String = FOUND

    override fun findByName(name: String): String = FOUND
}

const val FOUND = "found"

const val CALLS = 1_000

const val FIRST_ID = 1_000
