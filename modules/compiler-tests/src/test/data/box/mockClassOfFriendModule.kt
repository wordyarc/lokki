// MODULE: main
// FILE: Engine.kt
package app

abstract class Engine {
    internal abstract fun ignite(): Boolean

    internal open fun fuel(): Int = 100

    abstract fun start(): String

    fun run(): String = if (ignite()) start() else "no ignition"

    fun level(): Int = fuel()
}

// MODULE: test()(main)
// FILE: test.kt
package app.test

import app.Engine
import io.lokki.every
import io.lokki.mock

fun box(): String {
    val engine = mock<Engine>()
    every { engine.ignite() } returns true
    every { engine.start() } returns "started"
    every { engine.fuel() } returns 5

    if (engine.run() != "started") return "Fail: internal abstract member called from main"
    if (engine.level() != 5) return "Fail: internal open member called from main"
    if (!engine.ignite()) return "Fail: internal member called from the test"

    return "OK"
}
