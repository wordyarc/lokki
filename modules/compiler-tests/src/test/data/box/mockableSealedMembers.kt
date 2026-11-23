// MODULE: main
// MOCKABLE: sample.**
// FILE: main.kt
package sample

sealed class Definition(val type: String) {
    fun label(): String = "label:$type"

    private fun secret(): String = "secret"

    fun revealed(): String = secret()
}

class Concrete : Definition("concrete")

data class Described(val id: Int) : Definition("described")

open class Holder {
    open val definition: Definition = Concrete()
}

fun describe(definition: Definition): String =
    when (definition) {
        is Concrete -> "concrete"
        is Described -> "described"
    }

// MODULE: test()(main)
// FILE: test.kt
package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import java.lang.reflect.Modifier

fun box(): String {
    val holder = mock<Holder> { every { definition.type } returns "stubbed" }
    if (holder.definition.type != "stubbed") return "Fail chain through a sealed type"
    verify { holder.definition.type }

    val definition = mock<Definition> { every { label() } returns "mocked" }
    if (definition.label() != "mocked") return "Fail final member of a sealed class"

    val concrete = mock<Concrete> { every { type } returns "inherited" }
    if (concrete.type != "inherited") return "Fail member inherited from a sealed class"

    val spied = spy(Concrete())
    every { spied["secret"]() } returns "hidden"
    if (spied.revealed() != "hidden") return "Fail private member of a sealed class"

    if (Concrete().label() != "label:concrete") return "Fail real member"
    if (describe(Concrete()) != "concrete" || describe(Described(1)) != "described") return "Fail exhaustive when"
    if (!Modifier.isAbstract(Definition::class.java.modifiers)) return "Fail: the sealed class is not abstract"
    return "OK"
}
