// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// FILE: sample/Legacy.java
package sample;

public class Legacy {
    public int field;

    public String name() {
        return "";
    }

    public final String id() {
        return "";
    }

    public String getTitle() {
        return "";
    }

    public final String getCode() {
        return "";
    }

    public static String version() {
        return "";
    }
}

// FILE: test.kt
package sample

import io.lokki.coEvery
import io.lokki.every
import io.lokki.excludeRecords
import io.lokki.mock
import io.lokki.verify

interface Repo {
    val size: Int
    fun find(id: Int): String
    fun String.label(): String
}

abstract class Service {
    abstract fun load(): String
    open fun reload(): String = load()
    open suspend fun fetch(): String = load()
    suspend fun refresh(): String = load()
    fun cached(): String = load()
    inline fun <reified T> typed(): T = load() as T
    val state: String = ""
    open val status: String = ""

    @JvmField
    val field: String = ""
}

class Impl : Service() {
    override fun load(): String = ""
}

object Registry {
    const val LIMIT = 10
    fun lookup(): String = ""
}

enum class Color {
    RED;

    open fun hex(): String = ""
}

fun top(): String = ""

val topProperty: String = ""

fun Repo.first(): String = find(1)

val Repo.empty: Boolean
    get() = size == 0

fun test(repo: Repo, service: Service, impl: Impl, legacy: Legacy, text: String, function: (Int) -> String) {
    fun local(): String = ""

    every { <!LOKKI_NOT_INTERCEPTABLE!>top<!>() } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>topProperty<!> } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>local<!>() } returns "a"
    every { repo.<!LOKKI_NOT_INTERCEPTABLE!>first<!>() } returns "a"
    every { repo.<!LOKKI_NOT_INTERCEPTABLE!>empty<!> } returns true
    every { <!LOKKI_NOT_INTERCEPTABLE!>Impl<!>() } returns impl
    every { service.<!LOKKI_NOT_INTERCEPTABLE!>cached<!>() } returns "a"
    every { service.<!LOKKI_NOT_INTERCEPTABLE!>typed<!><String>() } returns "a"
    every { service.<!LOKKI_NOT_INTERCEPTABLE!>state<!> } returns "a"
    every { service.<!LOKKI_NOT_INTERCEPTABLE!>field<!> } returns "a"
    every { impl.<!LOKKI_NOT_INTERCEPTABLE!>load<!>() } returns "a"
    every { impl.<!LOKKI_NOT_INTERCEPTABLE!>toString<!>() } returns "a"
    every { Registry.<!LOKKI_NOT_INTERCEPTABLE!>lookup<!>() } returns "a"
    every { Registry.<!LOKKI_NOT_INTERCEPTABLE!>LIMIT<!> } returns 1
    every { Color.RED.<!LOKKI_NOT_INTERCEPTABLE!>hex<!>() } returns "a"
    every { text.<!LOKKI_NOT_INTERCEPTABLE!>length<!> } returns 1
    every { 1 <!LOKKI_NOT_INTERCEPTABLE!>+<!> 2 } returns 3
    every { legacy.<!LOKKI_NOT_INTERCEPTABLE!>id<!>() } returns "a"
    every { legacy.<!LOKKI_NOT_INTERCEPTABLE!>code<!> } returns "a"
    every { legacy.<!LOKKI_NOT_INTERCEPTABLE!>field<!> } returns 1
    every { Legacy.<!LOKKI_NOT_INTERCEPTABLE!>version<!>() } returns "a"
    every { <!LOKKI_NOT_INTERCEPTABLE!>toString<!>() } returns "a"
    every { this.<!LOKKI_NOT_INTERCEPTABLE!>hashCode<!>() } returns 1
    mock<Repo> { every { <!LOKKI_NOT_INTERCEPTABLE!>toString<!>() } returns "a" }
    coEvery { service.<!LOKKI_NOT_INTERCEPTABLE!>refresh<!>() } returns "a"
    coEvery { repo.<!LOKKI_NOT_INTERCEPTABLE!>first<!>() } returns "a"

    every { repo.find(1) } returns "a"
    every { repo.size } returns 1
    every { repo.toString() } returns "a"
    every { service.load() } returns "a"
    every { service.reload() } returns "a"
    every { service.status } returns "a"
    every { (service as Repo).find(1) } returns "a"
    every { legacy.name() } returns "a"
    every { legacy.title } returns "a"
    every { function(1) } returns "a"
    every { function.invoke(1) } returns "a"
    with(repo) { every { "a".label() } returns "a" }
    mock<Repo> { every { this@mock.toString() } returns "a" }
    mock<Repo> { every { find(1) } returns "a" }
    coEvery { service.fetch() } returns "a"
    coEvery { repo.find(1) } returns "a"

    verify { repo.<!LOKKI_NOT_INTERCEPTABLE!>first<!>() }
    verify { service.<!LOKKI_NOT_INTERCEPTABLE!>typed<!><String>() }
    verify { <!LOKKI_NOT_INTERCEPTABLE!>top<!>() }
    verify { service.reload() }

    excludeRecords { repo.<!LOKKI_NOT_INTERCEPTABLE!>first<!>() }
    excludeRecords { service.<!LOKKI_NOT_INTERCEPTABLE!>typed<!><String>() }
    excludeRecords { impl.<!LOKKI_NOT_INTERCEPTABLE!>load<!>() }
    excludeRecords { service.reload() }
}
