// FILE: sample/Spec.java
package sample;

public interface Spec<S extends Spec<S>> {
    S header(String name, String value);
    String retrieve();
}

// FILE: sample/UriSpec.java
package sample;

public interface UriSpec<S extends Spec<S>> {
    S uri(String uri);
}

// FILE: sample/HeadersUriSpec.java
package sample;

public interface HeadersUriSpec<S extends Spec<S>> extends UriSpec<S>, Spec<S> {
}

// FILE: Main.kt
package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock
import io.lokki.spy
import io.lokki.verify

open class Container<SELF : Container<SELF>> {
    @Suppress("UNCHECKED_CAST")
    open fun withName(name: String): SELF = this as SELF

    open fun name(): String = "real"
}

interface Node<T : Node<T>> {
    fun next(): T
    fun value(): Int
}

interface Car

interface Ordered<T : Comparable<T>> {
    fun max(): T
}

interface Both<T> where T : CharSequence, T : Comparable<T> {
    fun first(): T
}

interface Pairs<A, B : List<A>> {
    fun second(): B
}

interface Linked<A : Linked<A, B>, B : Linked<A, B>> {
    fun next(): B
    fun size(): Int
}

fun box(): String {
    val spec = mock<HeadersUriSpec<*>>()
    val headers = mock<Spec<*>>()
    every { spec.uri("/a") } returns headers
    every { headers.header("x", "1") } returns headers
    every { headers.retrieve() } returns "body"
    if (spec.uri("/a").header("x", "1").retrieve() != "body") return "Fail: Java self type"
    verify { spec.uri("/a") }
    every { spec.uri("/self") } returns spec
    every { spec.retrieve() } returns "self"
    if (spec.uri("/self").retrieve() != "self") return "Fail: Java self type returning itself"

    val container = mock<Container<*>>()
    every { container.withName("db") } returns container
    every { container.name() } returns "mocked"
    if (container.withName("db").name() != "mocked") return "Fail: class self type"

    val node = mock<Node<*>>()
    every { node.value() } returns 3
    every { node.next().value() } returns 4
    if (node.value() != 3 || node.next().value() != 4) return "Fail: Kotlin self type"

    val car = mock<Car>(moreInterfaces = arrayOf(Node::class, Ordered::class))
    if (car !is Node<*> || car !is Ordered<*>) return "Fail: added interfaces"

    val ordered = mock<Ordered<*>>()
    every { ordered.max() } returns "z"
    if (ordered.max() != "z") return "Fail: bound with the parameter"
    val both = mock<Both<*>>()
    every { both.first() } returns "a"
    if (both.first() != "a") return "Fail: several bounds"
    val pairs = mock<Pairs<*, *>>()
    every { pairs.second() } returns listOf("x")
    if (pairs.second() != listOf("x")) return "Fail: bound with another star"
    val linked = mock<Linked<*, *>>()
    val other = mock<Linked<*, *>>()
    every { linked.next() } returns other
    every { other.size() } returns 2
    if (linked.next().size() != 2) return "Fail: mutual bounds"

    val spied = spy<Container<*>>()
    if (spied.withName("db").name() != "real") return "Fail: spy of a class self type"
    every { spied.name() } returns "spied"
    if (spied.withName("db").name() != "spied") return "Fail: stub of a spy of a class self type"
    val delegating = spy(spec)
    if (delegating.uri("/a").retrieve() != "body" || delegating.uri("/self") !== spec) {
        return "Fail: delegating spy of a self type"
    }

    val relaxed = mock<HeadersUriSpec<*>>(relaxed = true)
    val noDefault = try {
        relaxed.uri("/b")
        "no exception"
    } catch (e: LokkiException) {
        e.message!!
    }
    val expected = "relaxed mock has no default for type parameter S returned by $relaxed.uri(/b); stub the call"
    if (noDefault != expected) return "Fail: relaxed: $noDefault"
    return "OK"
}
