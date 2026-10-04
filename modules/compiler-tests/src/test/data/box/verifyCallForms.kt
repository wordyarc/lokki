package sample

import io.lokki.MatcherScope
import io.lokki.Runs
import io.lokki.VerificationScope
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify

interface Shelf {
    var label: String
    fun find(id: Int): String
    fun findOrNull(id: Int): String?
    fun search(query: String, limit: Int = 10): List<String>
    fun names(vararg names: String): Int
    operator fun get(index: Int): String
    operator fun set(index: Int, book: String)
    operator fun plusAssign(book: String)
    operator fun contains(book: String): Boolean
    operator fun invoke(book: String): Int
}

fun MatcherScope.anyBook(): String = any()

fun VerificationScope.small(): Int = less(5)

fun one(): Int = 1

fun box(): String {
    val shelf = mock<Shelf> {
        every { label } returns "a"
        every { label = any() } just Runs
        every { find(any()) } returns "b"
        every { findOrNull(any()) } returns "c"
        every { search(any(), any()) } returns emptyList()
        every { names(any(), any()) } returns 2
        every { get(any()) } returns "d"
        every { set(any(), any()) } just Runs
        every { plusAssign(any()) } just Runs
        every { contains(any()) } returns true
        every { invoke(any()) } returns 3
    }
    val other = mock<Shelf> { every { find(any()) } returns "e" }

    shelf.findOrNull(1)
    shelf.find(2)
    shelf.search("kotlin")
    shelf.names("f", "g")
    shelf[3]
    shelf[4] = "h"
    shelf += "i"
    "j" in shelf
    shelf("k")
    shelf.label = "l"
    shelf.label
    other.find(5)

    verify { shelf.findOrNull(1)!! }
    verify { shelf.find(2) as CharSequence }
    verify { return@verify shelf.find(2) }
    verify(verifyBlock = { shelf.find(2) })
    verify(verifyBlock = { shelf.find(2) }, exactly = one())
    verify(verifyBlock = fun VerificationScope.(): Any? { return shelf.find(more(1)) })
    verify { shelf.search(anyBook()) }
    verify { shelf.search(limit = 10, query = any()) }
    verify { shelf.find(small()) }
    verify { shelf.names("f", any()) }
    verify { shelf.names(allAny(), "g") }
    verify { shelf[3] }
    verify { shelf[4] = "h" }
    verify { shelf += "i" }
    verify { "j" in shelf }
    verify { shelf("k") }
    verify { shelf.label = "l" }
    verify { shelf.label }
    verify {
        shelf.find(2)
        other.find(5)
        shelf[any()] = any()
    }
    verify(inverse = true) { other.find(2) }
    listOf(2).forEach { id -> verify { shelf.find(id) } }

    return "OK"
}
