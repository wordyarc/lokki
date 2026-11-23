// MODULE: main
// MOCKABLE: sample.**
// FILE: resources.kt
package sample

class Resource {
    fun load(): String = "real"

    protected fun finalize() {
        throw AssertionError("finalize of a mock")
    }
}

open class OpenResource {
    open fun load(): String = "real"

    protected open fun finalize() {}
}

interface Disposer {
    fun finalize(): Boolean
}

interface VoidDisposer {
    fun finalize()
}

interface ArgumentDisposer {
    fun finalize(value: Int): Boolean
}

// MODULE: test()(main)
// FILE: test.kt
package test

import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify
import java.lang.reflect.Modifier
import sample.ArgumentDisposer
import sample.Disposer
import sample.OpenResource
import sample.Resource
import sample.VoidDisposer

private fun Any.overridesFinalize(): Boolean = javaClass.declaredMethods.any { it.name == "finalize" }

fun box(): String {
    val finalizer = Resource::class.java.getDeclaredMethod("finalize")
    if (!Modifier.isFinal(finalizer.modifiers)) return "mockable opened finalize"

    val resource = mock<Resource>()
    every { resource.load() } returns "mock"
    if (resource.load() != "mock") return "load"
    if (resource.overridesFinalize()) return "mock of a mockable class overrides finalize"

    if (mock<OpenResource>().overridesFinalize()) return "mock of an open class overrides finalize"
    val spied = spy(OpenResource())
    if (spied.load() != "real") return "spy"
    if (spied.overridesFinalize()) return "spy overrides finalize"

    val disposer = mock<Disposer>()
    every { disposer.finalize() } returns true
    if (!disposer.finalize()) return "interface finalize"
    verify { disposer.finalize() }

    val void = mock<VoidDisposer>()
    every { void.finalize() } just Runs
    void.finalize()
    verify { void.finalize() }

    val argument = mock<ArgumentDisposer>()
    every { argument.finalize(5) } returns true
    if (!argument.finalize(5)) return "finalize with an argument"
    return "OK"
}
