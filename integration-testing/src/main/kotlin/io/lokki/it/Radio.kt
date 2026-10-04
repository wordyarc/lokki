package io.lokki.it

interface Radio {
    fun tune(station: Int, volume: Int = 5): String
}

class Dashboard(private val radio: Radio) {
    fun news(): String = radio.tune(101)

    fun loudNews(): String = radio.tune(101, volume = 11)
}
