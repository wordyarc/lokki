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

// FILE: sample/Token.java
package sample;

class Token {
    final String value;

    Token(String value) {
        this.value = value;
    }
}

// FILE: sample/Exchange.java
package sample;

public abstract class Exchange {
    public String send(Token token) {
        return "sent " + token.value;
    }

    public abstract Token echo(Token token);

    public static String sendThrough(Exchange exchange) {
        return exchange.send(new Token("a")) + " " + exchange.echo(new Token("b")).value;
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
    if (base.describe() != "hidden open!") return "Fail: ${base.describe()}"

    val exchange = mock<Exchange>()
    every { exchange.send(any()) } answers { callOriginal() }
    every { exchange.echo(any()) } returnsArgument 0
    return if (Exchange.sendThrough(exchange) == "sent a b") "OK" else "Fail: ${Exchange.sendThrough(exchange)}"
}
