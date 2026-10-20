// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// FILE: lib/Token.java
package lib;

class Token {
}

// FILE: lib/Exchange.java
package lib;

public abstract class Exchange {
    public abstract String send(Token token);

    public String sendAll(Token[] tokens) {
        return "all";
    }

    public Token reply() {
        return null;
    }

    public abstract int id();
}

// FILE: lib/Bounded.java
package lib;

public abstract class Bounded {
    protected abstract <T extends Token> void save(T token);
}

// FILE: lib/Holder.java
package lib;

public abstract class Holder {
    private static class Secret {
    }

    protected abstract void use(Secret secret);
}

// FILE: lib/Channel.java
package lib;

public interface Channel {
    default void open(Token token) {
    }
}

// FILE: lib/Fixed.java
package lib;

public abstract class Fixed {
    public final void send(Token token) {
    }

    void local(Token token) {
    }

    public abstract int id();
}

// FILE: lib/Inherited.java
package lib;

public abstract class Inherited extends Fixed {
    public abstract String name();
}

// FILE: lib/samePackage.kt
package lib

import io.lokki.mock

fun test() {
    mock<Exchange>()
    mock<Bounded>()
    mock<Holder>()
    mock<Channel>()
}

// FILE: main.kt
package main

import io.lokki.mock
import lib.Bounded
import lib.Channel
import lib.Exchange
import lib.Fixed
import lib.Holder
import lib.Inherited

fun test() {
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Exchange<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Bounded<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Holder<!>>()
    mock<<!LOKKI_TYPE_NOT_MOCKABLE!>Channel<!>>()
    mock<Runnable>(moreInterfaces = arrayOf(<!LOKKI_TYPE_NOT_MOCKABLE!>Channel::class<!>))
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Fixed<!>>()
    mock<<!LOKKI_FINAL_MEMBERS_NOT_MOCKED!>Inherited<!>>()
}
