package io.lokki.it.conformance

class ConstructedExample {
    val exampleProperty: Int = 1
}

interface ConstructedTokenProvider {
    fun token(): String
}

data class ConstructedWithRef(
    private val action: String,
    private val tokenProvider: ConstructedTokenProvider,
) {
    fun op() = "$action:${tokenProvider.token()}"
}

data class ConstructedCls(
    private val x: Int = 0,
) {
    constructor(x: String) : this(x.toInt())

    fun op(
        a: Int,
        b: Int,
    ) = a + b + x

    fun opList(
        a: Int,
        b: Int,
    ) = listOf(a, b)

    fun chainOp(
        a: Int,
        b: Int,
    ) = ConstructedCls(a + b + x)
}

class ConstructedPlain(
    private val x: Int = 0,
) {
    fun op(
        a: Int,
        b: Int,
    ) = a + b + x
}

class ConstructedOther {
    fun execute() = Unit
}

class ConstructedRegular {
    fun op(
        a: Int,
        b: Int,
    ) = a + b
}
