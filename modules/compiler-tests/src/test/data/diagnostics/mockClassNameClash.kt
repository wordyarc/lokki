// RUN_PIPELINE_TILL: BACKEND

// FILE: Car.kt
package sample

interface Car

// FILE: first/CarTest.kt
package sample

import io.lokki.mock

class FirstCarTest {
    val car = <!DUPLICATE_CLASS_NAMES!>mock<Car>()<!>
}

// FILE: second/CarTest.kt
package sample

import io.lokki.mock

class SecondCarTest {
    val car = <!DUPLICATE_CLASS_NAMES!>mock<Car>()<!>
}
