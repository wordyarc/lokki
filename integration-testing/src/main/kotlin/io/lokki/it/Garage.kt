package io.lokki.it

interface Car {
    val plate: String
    val radio: Radio
    fun drive(distance: Int): String
}

class Garage(private val car: Car) {
    fun leave(): String = car.drive(1)

    fun plate(): String = car.plate

    fun news(): String = car.radio.tune(101)

    override fun toString(): String = "Garage($car)"
}
