// MODULE: main
// INTERCEPTABLE: sample.**
// FILE: meter.kt
package sample

object InitLog {
    var fileInitialized = false
}

private val marker: String = run {
    InitLog.fileInitialized = true
    "initialized"
}

fun marker(): String = marker

class Meter {
    fun value(): String = "real"

    class Nested
}

// FILE: factory.kt
package sample

class MeterFactory {
    fun create(): Meter = Meter()

    fun nested(): Meter.Nested = Meter.Nested()
}

// FILE: plain.kt
package sample

class Plain

// MODULE: test()(main)
// FILE: test.kt
package sample

import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockConstructor

private fun classExists(name: String): Boolean =
    try {
        Class.forName(name)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

fun box(): String {
    val factory = MeterFactory()
    if (factory.create().value() != "real") return "Fail real constructor"
    factory.nested()
    if (InitLog.fileInitialized) return "Fail: a constructor call ran the initializers of the file"
    Plain()
    if (classExists("sample.PlainKt")) return "Fail: a file with only a class got a facade"

    val mocked = lokkiScope {
        mockConstructor(Meter::class)
        every { anyConstructed<Meter>().value() } returns "mocked"
        factory.create().value()
    }
    if (mocked != "mocked") return "Fail mockConstructor: $mocked"
    if (factory.create().value() != "real") return "Fail: the redirect outlived its scope"
    if (InitLog.fileInitialized) return "Fail: mockConstructor ran the initializers of the file"

    if (marker() != "initialized" || !InitLog.fileInitialized) return "Fail: file initializer"
    return "OK"
}
