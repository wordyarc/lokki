package io.lokki

public fun interface Matcher<in T> {
    public fun match(arg: T): Boolean
}
