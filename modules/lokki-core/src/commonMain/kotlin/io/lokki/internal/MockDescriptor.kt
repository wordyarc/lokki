package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal class MockDescriptor(internal val name: String, private val keys: Array<String>) {
    private val members by lazy {
        val members = HashMap<String, Int>()
        keys.forEachIndexed { member, aliases -> aliases.split(KEY_SEPARATOR).forEach { members[it] = member } }
        members
    }

    internal val memberCount: Int
        get() = keys.size

    internal fun member(key: String): Int = members[key] ?: throw LokkiException("$name has no member $key")

    internal fun memberName(member: Int): String {
        val key = keys[member].substringBefore(KEY_SEPARATOR)
        val parameters = key.lastIndexOf('(')
        return key.substring(key.lastIndexOfAny(charArrayOf('.', '#', ')'), parameters) + 1, parameters)
    }
}

private const val KEY_SEPARATOR = '|'
