package io.lokki.it

abstract class Mechanic(private val name: String) {
    internal abstract fun diagnose(car: Car): String

    open fun repair(car: Car): String = "repaired ${car.plate}"

    fun service(car: Car): String = "${diagnose(car)}, ${repair(car)}"

    fun signature(): String = "serviced by $name"
}
