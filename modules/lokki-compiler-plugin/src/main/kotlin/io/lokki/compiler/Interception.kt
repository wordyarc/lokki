package io.lokki.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal val REDIRECT = Name.identifier("LokkiRedirect")
internal val REDIRECT_PROPERTY = Name.identifier("lokki\$redirect")
internal const val FILE_REDIRECT_SUFFIX = "\$LokkiRedirect"
internal val CONSTRUCTORS = Name.identifier("LokkiConstructors")
internal const val CONSTRUCTORS_PROPERTY_SUFFIX = "\$lokkiConstructors"

internal const val INTERCEPTION_POINTS_KOTLIN = "2.4.20"

internal val INTERCEPTION_MARK = byteArrayOf(1)

internal enum class InterceptionMark {
    CURRENT,
    OTHER_FORMAT,
    ABSENT;

    companion object {
        fun of(data: ByteArray?): InterceptionMark =
            when {
                data == null -> ABSENT
                data.firstOrNull() == INTERCEPTION_MARK[0] -> CURRENT
                else -> OTHER_FORMAT
            }
    }
}

internal class StaticInterception(val redirect: ClassId, val property: Name) {
    fun encode(): ByteArray = INTERCEPTION_MARK + "${redirect.asString()}\n$property".encodeToByteArray()

    companion object {
        fun decode(data: ByteArray?): StaticInterception? {
            if (InterceptionMark.of(data) != InterceptionMark.CURRENT) return null
            val parts = data!!.decodeToString(startIndex = INTERCEPTION_MARK.size).split('\n')
            if (parts.size != 2) return null
            return StaticInterception(ClassId.fromString(parts[0]), Name.identifier(parts[1]))
        }
    }
}
