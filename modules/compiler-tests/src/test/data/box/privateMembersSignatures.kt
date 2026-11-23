// MODULE: main
// MOCKABLE: sample.**
// FILE: members.kt
package sample

private class FileLocal(val v: Int)

class Outer {
    private class Nested(val v: Int)

    fun viaNested() = nested(Nested(1)).v

    private fun nested(value: Nested) = value

    fun viaFile() = file(FileLocal(2)).v

    private fun file(value: FileLocal) = value

    fun viaList() = list(listOf(Nested(3))).single().v

    private fun list(value: List<Nested>) = value

    fun viaAnonymous() = anonymous().v

    private fun anonymous() = object {
        val v = 4
    }

    fun viaBound() = bound(Nested(5)).v

    private fun <T : Nested> bound(value: T): T = value

    fun plain() = hidden()

    private fun hidden() = "real"
}

object Holder {
    private class Secret(val v: Int)

    fun read() = secret(Secret(6)).v

    private fun secret(value: Secret) = value
}

enum class Mode {
    ON;

    private class Secret(val v: Int)

    fun read() = secret(Secret(7)).v

    private fun secret(value: Secret) = value
}

// MODULE: test(main)
// FILE: test.kt
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mockObject
import io.lokki.spy
import sample.Holder
import sample.Mode
import sample.Outer

fun box(): String = lokkiScope {
    val outer = spy<Outer>()
    if (outer.viaNested() != 1) return@lokkiScope "nested"
    if (outer.viaFile() != 2) return@lokkiScope "file"
    if (outer.viaList() != 3) return@lokkiScope "list"
    if (outer.viaAnonymous() != 4) return@lokkiScope "anonymous"
    if (outer.viaBound() != 5) return@lokkiScope "bound"
    every { outer["hidden"]() } returns "stub"
    if (outer.plain() != "stub") return@lokkiScope "plain"
    mockObject(Holder, Mode.ON) {
        if (Holder.read() != 6) return@lokkiScope "object"
        if (Mode.ON.read() != 7) return@lokkiScope "enum"
    }
    "OK"
}
