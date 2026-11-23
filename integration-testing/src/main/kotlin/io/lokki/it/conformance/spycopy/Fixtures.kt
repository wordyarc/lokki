package io.lokki.it.conformance.spycopy

class Person {
    val firstName: String = "Alan"
    val surname: String = "Turing"

    val fullname by lazy {
        "$firstName $surname"
    }
}

class Counter {
    var hits: Int = 0

    val report by lazy {
        "hits=$hits"
    }
}
