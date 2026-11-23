// MODULE: main
// MOCKABLE: sample.**
// FILE: holder.kt
package sample

class Holder {
    var intProp: Int = 5

    val constant: String = "field"
}

object Settings {
    var level: Int = 1
}

// MODULE: test(main)
// FILE: test.kt
import io.lokki.LokkiException
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockObject
import io.lokki.spy
import sample.Holder
import sample.Settings

fun box(): String = lokkiScope {
    val read = spy(Holder())
    every { read.intProp } answers { fieldValue + 6 }
    if (read.intProp != 11) return@lokkiScope "read"

    val write = spy(Holder())
    every { write.intProp = any() } propertyType Int::class answers { fieldValue += value }
    write.intProp = 3
    write.intProp = 4
    if (write.intProp != 12) return@lokkiScope "write"

    val chain = spy(Holder())
    every { chain.intProp = any() } propertyType Int::class answers {
        fieldValue = value + 1
    } andThenAnswer {
        fieldValue = value - 1
    }
    chain.intProp = 3
    if (chain.intProp != 4) return@lokkiScope "first answer"
    chain.intProp = 4
    if (chain.intProp != 3) return@lokkiScope "second answer"

    val dynamic = spy(Holder())
    every { dynamic getProperty "intProp" } propertyType Int::class answers { fieldValue + 6 }
    if (dynamic.intProp != 11) return@lokkiScope "getProperty"

    val any = spy(Holder())
    every { any.intProp } answers { (fieldValueAny as Int) * 2 }
    if (any.intProp != 10) return@lokkiScope "fieldValueAny"

    val constant = spy(Holder())
    every { constant.constant } answers {
        try {
            fieldValue = "x"
            "written"
        } catch (e: LokkiException) {
            if ("read-only" in e.message.orEmpty()) fieldValue else "message"
        }
    }
    if (constant.constant != "field") return@lokkiScope "read-only: ${constant.constant}"

    mockObject(Settings) {
        every { Settings.level } answers { fieldValue + 1 }
        if (Settings.level != 2) return@lokkiScope "object"
    }
    "OK"
}
