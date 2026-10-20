package sample

import io.lokki.LokkiException
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import java.io.Closeable

interface Car {
    fun drive(speed: Int): String
}

interface Named {
    val name: String
    fun rename(name: String)
}

interface Counter : Named {
    fun count(): Int
}

interface Greeter {
    fun greet(): String = "hello"
}

interface Sized {
    val size: Number
}

interface IntSized {
    val size: Int
}

open class Engine(val power: Int) {
    open fun start(): Boolean = true
}

private fun <T> answer(block: () -> T): Any? =
    try {
        block()
    } catch (e: LokkiException) {
        e.message
    }

fun box(): String {
    val car = mock<Car>("car", moreInterfaces = arrayOf(Named::class, Closeable::class))
    if (car !is Named || car !is Closeable) return "Fail: the mock does not implement the interfaces"
    if (car.javaClass.simpleName != "Car\$LokkiMock\$37e6323b\$MockMoreInterfacesKt") {
        return "Fail name: ${car.javaClass.simpleName}"
    }
    every { car.drive(1) } returns "drive"
    every { (car as Named).name } returns "named"
    every { (car as Closeable).close() } just Runs
    if (car.drive(1) != "drive" || (car as Named).name != "named") return "Fail: stubs"
    (car as Closeable).close()
    verify { (car as Named).name }
    verify { (car as Closeable).close() }
    val strict = answer { (car as Named).rename("x") } as String
    if (!strict.startsWith("no answer found for Car(car#") || !strict.contains(".rename(x) among")) {
        return "Fail strict: $strict"
    }

    val positional = mock<Car>("positional", false, Named::class)
    val spread = mock<Car>(moreInterfaces = *arrayOf(Named::class))
    if (positional.javaClass != spread.javaClass) return "Fail: positional and spread interfaces"
    if (positional.javaClass.simpleName != "Car\$LokkiMock\$a7d52d8b\$MockMoreInterfacesKt") return "Fail: one interface"

    val reordered = mock<Car>(moreInterfaces = arrayOf(Closeable::class, Named::class, Named::class))
    if (reordered.javaClass != car.javaClass) return "Fail: order and duplicates of interfaces"
    if (mock<Car>(moreInterfaces = arrayOf(Car::class)).javaClass != mock<Car>().javaClass) return "Fail: the mocked type"
    if (mock<Counter>(moreInterfaces = arrayOf(Named::class)).javaClass != mock<Counter>().javaClass) {
        return "Fail: an inherited interface"
    }
    if (mock<Car>(moreInterfaces = arrayOf(Named::class, Counter::class)).javaClass !=
        mock<Car>(moreInterfaces = arrayOf(Counter::class)).javaClass
    ) {
        return "Fail: an interface inherited by another one"
    }

    val relaxed = mock<Car>(relaxed = true, moreInterfaces = arrayOf(Counter::class))
    if ((relaxed as Counter).count() != 0 || relaxed.name != "" || relaxed.drive(1) != "") return "Fail: relaxed"

    val greeter = mock<Car>(moreInterfaces = arrayOf(Greeter::class))
    every { (greeter as Greeter).greet() } answers { callOriginal() + "!" }
    if ((greeter as Greeter).greet() != "hello!") return "Fail: callOriginal of a default method"

    val engine = mock<Engine>(moreInterfaces = arrayOf(Named::class))
    every { engine.start() } returns false
    every { (engine as Named).name } returns "v8"
    if (engine.start() || (engine as Named).name != "v8") return "Fail: class with an interface"
    every { engine.start() } answers { callOriginal() }
    if (!engine.start()) return "Fail: callOriginal of a class with an interface"

    val comparable = mock<Car>(moreInterfaces = arrayOf(Comparable::class))
    if (comparable.javaClass.simpleName != "Car\$LokkiMock\$2b0e6b49\$MockMoreInterfacesKt") {
        return "Fail approximated name: ${comparable.javaClass.simpleName}"
    }
    @Suppress("UNCHECKED_CAST")
    val compared = comparable as Comparable<Any?>
    every { compared.compareTo(any()) } returns 1
    if (compared.compareTo("a") != 1) return "Fail: generic interface"

    val runnable = mock<Car>(relaxUnitFun = true, moreInterfaces = arrayOf(Runnable::class))
    (runnable as Runnable).run()
    verify { runnable.run() }

    val sized = mock<IntSized>(moreInterfaces = arrayOf(Sized::class))
    every { sized.size } returns 3
    if ((sized as Sized).size != 3) return "Fail: covariant property"

    return "OK"
}
