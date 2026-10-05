// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: lib
// FILE: lib.kt
package lib

open class Library(val id: Int) {
    fun describe(): String = "library"
}

// MODULE: main
// FILE: main.kt
package main

open class Service(val dependency: Any) {
    fun count(): Int = 1

    open fun fetch(): Int = 1

    protected fun hook(): Int = 1

    private fun hidden(): Int = 1

    final override fun toString(): String = "service"
}

// MODULE: test(lib)(main)
// FILE: test.kt
package test

import io.lokki.mock
import lib.Library
import main.Service

abstract class Task {
    val name: String = "task"

    abstract fun run()
}

open class Clean {
    open fun run() {}
}

interface Api {
    fun run()
}

fun test() {
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Service<!>>()
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Task<!>>()
    mock<Library>()
    mock<Clean>()
    mock<Api>()
    mock<Thread>()
}
