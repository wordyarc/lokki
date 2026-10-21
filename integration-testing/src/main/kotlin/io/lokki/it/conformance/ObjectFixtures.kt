package io.lokki.it.conformance

object MockObj {
    fun add(
        a: Int,
        b: Int,
    ) = a + b
}

object SyntheticStaticHolder {
    @JvmStatic
    @JvmSynthetic
    fun compute(arg: String): String = "real-$arg"
}

object ObjectTarget {
    fun value() = 0
}
