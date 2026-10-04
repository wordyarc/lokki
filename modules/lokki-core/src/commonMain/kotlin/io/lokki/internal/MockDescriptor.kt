package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal class MockDescriptor(
    internal val name: String,
    private val keys: Array<String>,
    private val aliases: Array<String>,
) {
    constructor(name: String, keys: Array<String>) : this(name, keys, emptyArray())

    private val members by lazy {
        val members = HashMap<String, Int>()
        keys.forEachIndexed { member, key -> members.add(key, member) }
        for (index in aliases.indices step 2) members.add(aliases[index], members.getValue(aliases[index + 1]))
        members
    }

    internal val memberCount: Int
        get() = keys.size

    internal fun member(key: String): Int = members[key] ?: throw LokkiException("$name has no member $key")

    internal fun memberName(member: Int): String {
        val key = keys[member]
        val parameters = key.lastIndexOf('(')
        if (key[parameters - 1] != '>') {
            return key.substring(key.lastIndexOfAny(NAME_DELIMITERS, parameters) + 1, parameters)
        }
        val accessor = key.lastIndexOf('<', parameters)
        val property = key.substring(accessor + ACCESSOR_PREFIX_LENGTH, parameters - 1)
        val boolean = property.length > 2 && property.startsWith("is") && property[2] !in 'a'..'z'
        return when {
            !key.startsWith("<get-", accessor) -> "set" + if (boolean) property.substring(2) else property.capitalized()
            boolean -> property
            else -> "get" + property.capitalized()
        }
    }

    private fun HashMap<String, Int>.add(key: String, member: Int) {
        if (put(key, member) != null) throw LokkiException("$name has two members with the key $key")
    }
}

private val NAME_DELIMITERS = charArrayOf('.', '#', ')', '>')
private const val ACCESSOR_PREFIX_LENGTH = "<get-".length

private fun String.capitalized(): String = if (first() in 'a'..'z') first().uppercaseChar() + substring(1) else this
