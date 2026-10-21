package io.lokki.it

open class Workshop(private val name: String, private val capacity: Int = 2) {
    private val log = ArrayList<String>()

    init {
        open()
    }

    open fun open() {
        log += "open"
    }

    internal open fun schedule(car: Car): String = "${car.plate} at $name"

    open fun service(cars: List<Car>): List<String> = cars.take(capacity).map { schedule(it) }

    open suspend fun inspect(car: Car): String = "inspected ${car.plate}"

    fun log(): List<String> = log
}
