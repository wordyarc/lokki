// MODULE: lib
// FILE: Lib.kt
package lib

interface Repo {
    fun find(id: Int): String
}

abstract class Holder<T : Any> {
    lateinit var held: T

    var optional: Repo? = null
}

open class Service(val repo: Repo, val name: String = "service") : Holder<Repo>() {
    open fun find(id: Int): String = repo.find(id)
}

// MODULE: main(lib)
// FILE: Main.kt
package app

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.Spy
import io.lokki.every
import lib.Repo
import lib.Service

class Fixture {
    @Mock
    lateinit var repo: Repo

    @InjectMocks
    lateinit var service: Service

    @InjectMocks
    @Spy
    lateinit var spied: Service
}

fun box(): String {
    val fixture = Fixture()
    MockAnnotations.init(fixture)
    val service = fixture.service
    if (service.repo !== fixture.repo || service.name != "service") return "Fail: constructor"
    if (service.held !== fixture.repo || service.optional !== fixture.repo) return "Fail: inherited properties"
    every { fixture.repo.find(1) } returns "one"
    if (fixture.spied.find(1) != "one" || fixture.spied.held !== fixture.repo) return "Fail: spy"
    return "OK"
}
