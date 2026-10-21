// FILE: app/Garage.java
package app;

public class Garage {
    private final Car car;
    private final Door door;
    public Listener listener;

    public Garage(Car car) {
        this(car, null);
    }

    public Garage(Car car, Door door) {
        this.car = car;
        this.door = door;
    }

    public String open() {
        return car.drive() + (door == null ? "" : door.state());
    }
}

// FILE: Main.kt
package app

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.MockAnnotations
import io.lokki.every

interface Car {
    fun drive(): String
}

interface Door {
    fun state(): String
}

interface Listener

class Fixture {
    @Mock
    lateinit var car: Car

    @Mock
    lateinit var door: Door

    @Mock
    lateinit var listener: Listener

    @InjectMocks
    lateinit var garage: Garage
}

fun box(): String {
    val fixture = Fixture()
    MockAnnotations.init(fixture)
    every { fixture.car.drive() } returns "driven"
    every { fixture.door.state() } returns " and open"
    if (fixture.garage.open() != "driven and open") return "Fail: ${fixture.garage.open()}"
    if (fixture.garage.listener != null) return "Fail: Java fields are not injected"
    return "OK"
}
