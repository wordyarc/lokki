// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// FILE: JavaStatics.java
public class JavaStatics {
    public static int now() { return 1; }
}

// FILE: test.kt
import io.lokki.mockStatic
import java.time.LocalDate
import kotlin.reflect.KClass

class Plain

object Local

fun test(type: KClass<*>) {
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>JavaStatics::class<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>LocalDate::class<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Plain::class<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>Local::class<!>)
    mockStatic(<!LOKKI_OBJECT_NOT_INTERCEPTABLE!>type<!>)
}
