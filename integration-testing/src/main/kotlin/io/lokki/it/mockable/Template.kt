package io.lokki.it.mockable

class Template {
    fun run(): String = "result=" + hook(1)

    protected fun hook(value: Int): String = "real:$value"

    protected fun finalize() {
        throw AssertionError("class-level finalize must never be invoked through the mock")
    }
}
