// CHECK_BYTECODE_TEXT
// FILE: sample/Source.java
package sample;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public interface Source {
    List<String> list();
    Map<String, Object> map();
    Set<String> set();
    Collection<String> collection();
    Iterable<String> iterable();
    Iterator<String> iterator();
    ListIterator<String> listIterator();
    Map.Entry<String, String> entry();
    String name();
}

// FILE: sample/Sink.java
package sample;

import java.util.List;
import java.util.Map;

public class Sink {
    public int count(List<String> values, Map<String, String> pairs) {
        return values.size() + pairs.size();
    }
}

// FILE: Main.kt
package sample

import io.lokki.every
import io.lokki.mock
import io.lokki.spy

private object Entry : Map.Entry<String, String> {
    override val key: String
        get() = "k"
    override val value: String
        get() = "v"
}

fun box(): String {
    val source = mock<Source>()
    every { source.list() } returns emptyList()
    every { source.map() } answers { emptyMap() }
    every { source.set() } returns emptySet()
    every { source.collection() } returns emptyList()
    every { source.iterable() } returns emptySet()
    every { source.iterator() } returns emptyList<String>().iterator()
    every { source.listIterator() } returns emptyList<String>().listIterator()
    every { source.entry() } returns Entry
    every { source.name() } returns null

    if (source.list() != emptyList<String>()) return "Fail: list"
    if (source.map() != emptyMap<String, Any>()) return "Fail: map"
    if (source.set() != emptySet<String>()) return "Fail: set"
    if (!source.collection().isEmpty()) return "Fail: collection"
    if (source.iterable().any()) return "Fail: iterable"
    if (source.iterator().hasNext()) return "Fail: iterator"
    if (source.listIterator().hasNext()) return "Fail: list iterator"
    if (source.entry() !== Entry) return "Fail: entry"
    if (source.name() != null) return "Fail: platform type"

    if (spy<Sink>().count(emptyList(), emptyMap()) != 0) return "Fail: spy"
    if (spy(Sink()).count(listOf("a"), emptyMap()) != 1) return "Fail: delegating spy"

    return "OK"
}

// 0 TypeIntrinsics\.asMutable
