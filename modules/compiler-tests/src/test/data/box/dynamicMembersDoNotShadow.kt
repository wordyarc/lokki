// FILE: test.kt
import io.lokki.every
import io.lokki.lokkiScope
import io.lokki.mock
import java.util.Properties

interface Remote {
    fun invokeNoArgs(name: String): String

    fun setProperty(name: String): String

    fun invoke(name: String): String
}

fun box(): String = lokkiScope {
    val properties = mock<Properties> {
        every { getProperty("key") } returns "value"
    }
    if (properties.getProperty("key") != "value") return@lokkiScope "getProperty"
    val remote = mock<Remote> {
        every { invokeNoArgs("a") } returns "1"
        every { setProperty("b") } returns "2"
    }
    every { remote.invoke("c") } returns "3"
    if (remote.invokeNoArgs("a") != "1") return@lokkiScope "invokeNoArgs"
    if (remote.setProperty("b") != "2") return@lokkiScope "setProperty"
    if (remote.invoke("c") != "3") return@lokkiScope "invoke"
    "OK"
}
