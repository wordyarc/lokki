// FILE: Car.kt
package sample

interface Car {
    fun drive(): String
}

// FILE: Garage.kt
package sample

import io.lokki.mock

fun garageCar(): Car = mock<Car>()

// FILE: Track.kt
package sample

import io.lokki.mock

class Track {
    val car: Car = mock<Car>()
}

// FILE: box.kt
package sample

fun box(): String {
    val garageClass = garageCar().javaClass.name
    val trackClass = Track().car.javaClass.name

    if (garageClass != "sample.Car\$LokkiMock\$08768170\$GarageKt") return "Fail: $garageClass"
    if (trackClass != "sample.Car\$LokkiMock\$08768170\$TrackKt") return "Fail: $trackClass"

    return "OK"
}
