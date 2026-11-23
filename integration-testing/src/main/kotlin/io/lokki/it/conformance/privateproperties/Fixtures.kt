@file:Suppress("unused", "UNUSED_PARAMETER", "ProtectedInFinal")
package io.lokki.it.conformance.privateproperties

data class Person(
    var name: String,
)

@Suppress("ProtectedInFinal", "RedundantSetter")
class Team {
    protected var person: Person = Person("Init")
        get() = Person("Ben")
        set(value) {
            field = value
        }

    protected fun fn(arg: Int): Int = arg + 5

    fun pubFn(arg: Int) = fn(arg)

    var memberName: String
        get() = person.name
        set(value) {
            person = Person(value)
        }
}
