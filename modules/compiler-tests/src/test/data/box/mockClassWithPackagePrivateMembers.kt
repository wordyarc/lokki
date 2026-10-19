// FILE: sample/Base.java
package sample;

public abstract class Base {
    abstract String hidden();

    String open() {
        return "open";
    }

    public String describe() {
        return hidden() + " " + open();
    }
}

// FILE: test.kt
package sample

import io.lokki.every
import io.lokki.mock

fun box(): String {
    val base = mock<Base>()
    every { base.hidden() } returns "hidden"
    every { base.open() } answers { callOriginal() + "!" }
    every { base.describe() } answers { callOriginal() }
    return if (base.describe() == "hidden open!") "OK" else "Fail: ${base.describe()}"
}
