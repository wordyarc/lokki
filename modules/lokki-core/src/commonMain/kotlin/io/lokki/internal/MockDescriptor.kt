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

    private val privateMembers: BooleanArray? by lazy {
        BooleanArray(keys.size) { LokkiAbi.PRIVATE_HOOK_INFIX in keys[it] }.takeIf { flags -> flags.any { it } }
    }

    internal val memberCount: Int
        get() = keys.size

    internal fun isPrivate(member: Int): Boolean = privateMembers?.get(member) == true

    internal fun accessor(member: Int, access: FieldAccess): Int? {
        val signature = keys[member].substringAfter('#')
        val property = ACCESSOR.find(signature) ?: return null
        val prefix = signature.substring(0, property.range.first)
        val wanted = "$prefix<${access.prefix}-${property.groupValues[1]}>("
        return keys.indexOfFirst { it.substringAfter('#').startsWith(wanted) }.takeIf { it >= 0 }
    }

    internal fun hasSameMembers(other: MockDescriptor): Boolean = keys.contentEquals(other.keys)

    internal fun member(key: String): Int = members[key] ?: throw LokkiException("$name has no member $key")

    internal fun description(mock: GeneratedMock, member: Int): MethodDescription =
        descriptions[member] ?: checkNotNull(mock.`lokki$describe`(member)) { "$name has no member $member" }
            .also { descriptions[member] = it }

    private fun HashMap<String, Int>.add(key: String, member: Int) {
        if (put(key, member) != null) throw LokkiException("$name has two members with the key $key")
    }
}

internal enum class FieldAccess(val prefix: String) {
    READ("get"),
    WRITE("set"),
}

private val ACCESSOR = Regex("<[gs]et-(.+?)>\\(")
