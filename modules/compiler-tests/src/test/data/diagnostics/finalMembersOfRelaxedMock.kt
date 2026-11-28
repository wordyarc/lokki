// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// MODULE: lib
// FILE: lib/Response.java
package lib;

public class Response implements Comparable<Response> {
    private final String task;

    private Response(String task) {
        this.task = task;
    }

    public static Response of(String task) {
        return new Response(task);
    }

    public final String task() {
        return task;
    }

    public final boolean completed() {
        return task != null;
    }

    protected void serializeInternal(StringBuilder builder) {}

    @Override
    public int compareTo(Response other) {
        return 0;
    }

    @Override
    public int hashCode() {
        return 1;
    }
}

// FILE: lib/Gateway.java
package lib;

public abstract class Gateway {
    public abstract String send(String body);

    public final String sendTwice(String body) {
        return send(body) + send(body);
    }
}

// FILE: lib/Point.java
package lib;

public class Point {
    public int x;

    public final int y() {
        return 0;
    }
}

// FILE: lib/Coordinates.java
package lib;

public class Coordinates {
    public int x;

    public final int y = 0;
}

// FILE: lib/lib.kt
package lib

open class Library(val id: Int) {
    fun describe(): String = "library"
}

open class Settings {
    val name: String = "settings"

    open fun reload() {}
}

open class Empty

// MODULE: test(lib)
// FILE: test.kt
package test

import io.lokki.Mock
import io.lokki.RelaxedMock
import io.lokki.mock
import io.lokki.mockClass
import lib.Coordinates
import lib.Empty
import lib.Gateway
import lib.Library
import lib.Point
import lib.Response
import lib.Settings

class Fields(<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>@RelaxedMock<!> val parameter: Response) {
    <!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>@RelaxedMock<!>
    lateinit var response: Response

    <!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>@Mock(relaxed = true)<!>
    lateinit var library: Library

    @Mock
    lateinit var strict: Response

    @Mock(relaxUnitFun = true)
    lateinit var unitFunctions: Response
}

fun test(relaxed: Boolean) {
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Response<!>>(relaxed = true)
    <!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>mockClass(Response::class, relaxed = true)<!>
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Library<!>>(relaxed = true)
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Point<!>>(relaxed = true)
    mock<Response>()
    mock<Response>(relaxed = relaxed)
    mock<Response>(relaxUnitFun = true)
    mock<Library>()
    mock<Gateway>(relaxed = true)
    mock<Settings>(relaxed = true)
    mock<Empty>(relaxed = true)
    mock<Coordinates>(relaxed = true)
    mock<RuntimeException>(relaxed = true)
}
