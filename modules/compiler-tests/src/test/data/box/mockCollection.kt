package sample

import io.lokki.LokkiException
import io.lokki.every
import io.lokki.mock

fun box(): String {
    val list = mock<List<String>>()
    every { list.size } returns 2
    every { list[0] } returns "a"
    every { list.contains("a") } returns true
    if (list.size != 2 || list[0] != "a" || "a" !in list) return "Fail list"
    if ((list as java.util.Collection<*>).contains(1)) return "Fail: contains of another type reached the mock"
    try {
        @Suppress("UNCHECKED_CAST")
        (list as java.util.List<String>).add("b")
        return "Fail: add on a read-only list mock"
    } catch (e: UnsupportedOperationException) {
    }

    val mutable = mock<MutableList<String>>()
    every { mutable.add("b") } returns true
    every { mutable.removeAt(0) } returns "x"
    if (!mutable.add("b") || mutable.removeAt(0) != "x") return "Fail mutable list"

    val map = mock<MutableMap<String, Int>>()
    every { map["a"] } returns 1
    every { map.put("b", 2) } returns null
    if (map["a"] != 1 || map.put("b", 2) != null) return "Fail map"

    val hashMap = mock<HashMap<String, String>>()
    every { hashMap["abc"] } returns "def"
    every { hashMap.put("ghi", "klm") } returns "nop"
    every { hashMap.size } answers { callOriginal() + 10 }
    if (hashMap["abc"] != "def" || hashMap.put("ghi", "klm") != "nop") return "Fail hash map"
    if (hashMap.size != 10) return "Fail original of a Java collection: ${hashMap.size}"
    val strict = try {
        hashMap.isEmpty()
        "no exception"
    } catch (e: LokkiException) {
        e.message
    }
    if (strict?.startsWith("no answer found for HashMap(#") != true) return "Fail strict: $strict"

    val set = mock<Set<Int>>()
    every { set.iterator() } returns listOf(1, 2).iterator()
    if (set.iterator().asSequence().toList() != listOf(1, 2)) return "Fail set"
    return "OK"
}
