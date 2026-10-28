package io.lokki.internal

import io.lokki.LokkiException
import io.lokki.MethodDescription

@PublishedApi
internal class MockDescriptor(
    internal val name: String,
    internal val abi: Int,
    private val keys: Array<String>,
    private val aliases: Array<String>,
) {
    constructor(name: String, abi: Int, keys: Array<String>) : this(name, abi, keys, emptyArray())

    private val members by lazy {
        val members = HashMap<String, Int>()
        keys.forEachIndexed { member, key -> members.add(key, member) }
        for (index in aliases.indices step 2) members.add(aliases[index], members.getValue(aliases[index + 1]))
        members
    }

    private val descriptions = arrayOfNulls<MethodDescription>(keys.size)

    internal val memberCount: Int
        get() = keys.size

    internal fun member(key: String): Int = members[key] ?: throw LokkiException("$name has no member $key")

    internal fun description(mock: GeneratedMock, member: Int): MethodDescription =
        descriptions[member] ?: checkNotNull(mock.`lokki$describe`(member)) { "$name has no member $member" }
            .also { descriptions[member] = it }

    private fun HashMap<String, Int>.add(key: String, member: Int) {
        if (put(key, member) != null) throw LokkiException("$name has two members with the key $key")
    }
}
