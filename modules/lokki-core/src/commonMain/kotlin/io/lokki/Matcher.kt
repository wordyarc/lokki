package io.lokki

public interface Matcher<in T> {
    public fun match(arg: T?): Boolean
}
