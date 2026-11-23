// MODULE: main
// MOCKABLE: sample.**
// FILE: team.kt
package sample

class Team {
    protected var person: String = "Init"
        get() = "Ben"
        set(value) {
            field = value
        }

    protected fun fn(arg: Int): Int = arg + 5

    protected fun none(): String = "real"

    fun pubFn(arg: Int) = fn(arg)

    fun pubNone() = none()

    var memberName: String
        get() = person
        set(value) {
            person = value
        }
}

// MODULE: test(main)
// FILE: test.kt
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.lokkiScope
import io.lokki.spy
import io.lokki.verify
import sample.Team

fun box(): String = lokkiScope {
    val team = spy<Team>()
    every { team getProperty "person" } returns "Big Ben"
    every { team setProperty "person" value "test" } just Runs
    every { team invoke "fn" withArguments listOf(5) } returns 3
    every { team invokeNoArgs "none" } returns "stub"
    if (team.memberName != "Big Ben") return@lokkiScope "getProperty"
    if (team.pubFn(5) != 3) return@lokkiScope "withArguments"
    if (team.pubFn(1) != 6) return@lokkiScope "original"
    if (team.pubNone() != "stub") return@lokkiScope "invokeNoArgs"
    team.memberName = "test"
    verify { team getProperty "person" }
    verify { team setProperty "person" value "test" }
    verify { team invoke "fn" withArguments listOf(5) }
    verify { team invokeNoArgs "none" }
    every { team invoke "fn" withArguments listOf(any<Int>()) } returns 9
    if (team.pubFn(100) != 9) return@lokkiScope "matcher in the list"
    "OK"
}
