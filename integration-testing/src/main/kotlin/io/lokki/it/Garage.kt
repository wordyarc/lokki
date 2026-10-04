package io.lokki.it

interface Car {
    val plate: String
    fun drive(distance: Int): String
}

class Garage(private val car: Car) {
    fun leave(): String = car.drive(1)

    fun plate(): String = car.plate
}
