@file:Suppress("unused", "UNUSED_PARAMETER", "ProtectedInFinal")
package io.lokki.it.conformance.privateparent

open class Parent {
    open fun call(): String = callPrivate()

    private fun callPrivate() = "Real"
}

open class Child : Parent()

class ChildWithShadowedMethod : Parent() {
    override fun call(): String = callPrivate()

    fun callPrivate() = "Shadowed"
}

class GrandChild : Child()
