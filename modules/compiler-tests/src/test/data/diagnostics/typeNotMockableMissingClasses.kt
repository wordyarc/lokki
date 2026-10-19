// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: dep
// FILE: dep/Missing.kt
package dep

open class Missing

interface MissingApi

// MODULE: lib(dep)
// FILE: lib/Lib.kt
package lib

import dep.Missing
import dep.MissingApi

abstract class Service {
    abstract fun load(): Missing

    abstract fun id(): Int
}

abstract class Plugin {
    open fun optional(): Missing? = null

    abstract fun id(): Int
}

abstract class ExtendedPlugin : Plugin()

interface Repository {
    fun optional(): Missing? = null

    fun id(): Int
}

abstract class Base {
    private fun helper(): Missing? = null

    fun used(): Boolean = helper() == null
}

abstract class Derived : Base() {
    abstract fun id(): Int
}

interface Store {
    fun saveAll(items: Array<Missing>)

    fun <T : Missing> save(item: T)

    var current: Missing?
}

abstract class Lists {
    abstract fun all(): List<Missing>

    abstract fun id(): Int
}

abstract class Subclass : Missing()

interface Handler : MissingApi

abstract class Grandchild : Subclass()

// MODULE: main(lib)
// FILE: main.kt
package main

import io.lokki.mock
import lib.Derived
import lib.ExtendedPlugin
import lib.Grandchild
import lib.Handler
import lib.Lists
import lib.Plugin
import lib.Repository
import lib.Service
import lib.Store
import lib.Subclass

fun test() {
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Service<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Plugin<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>ExtendedPlugin<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Repository<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Derived<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Store<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Subclass<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Handler<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Grandchild<!>>()
    mock<Lists>()
}
