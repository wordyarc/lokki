package io.lokki.it.conformance.types

class TestCls

open class TestSub

class TestSubChild : TestSub()

object TestObject {
    fun value() = 1
}

fun staticValue() = 1

fun newTestCls() = TestCls()
