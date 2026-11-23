@file:Suppress("unused", "UNUSED_PARAMETER", "ProtectedInFinal")
package io.lokki.it.conformance.privatefunctions

class PrivateNoReturnCls {
    fun myPublicMethod() {
        myPrivateMethod()
    }

    private fun myPrivateMethod() {
    }
}

class Abc {
    fun y() = x()

    private fun x() = "abc"
}

object Def {
    fun y() = x()

    private fun x() = "abc"
}

class MockCls {
    fun y(
        a: Int,
        b: Int?,
        d: Def?,
    ) = x(a, b, d)

    private fun x(
        a: Int,
        b: Int?,
        d: Def?,
    ) = "abc $a $b"
}

class GenericsCls {
    private fun <T> updateItemInDb(
        id: Long,
        column: String,
        data: T,
    ) {
    }

    fun pubCall() {
        updateItemInDb(1L, "abc", "data")
    }
}
