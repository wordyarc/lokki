package io.lokki.compiler

import org.jetbrains.kotlin.name.Name

internal val REDIRECT = Name.identifier("LokkiRedirect")
internal val REDIRECT_PROPERTY = Name.identifier("lokki\$redirect")

internal val INTERCEPTION_MARK = byteArrayOf(1)

internal enum class InterceptionMark {
    CURRENT,
    OTHER_FORMAT,
    ABSENT;

    companion object {
        fun of(data: ByteArray?): InterceptionMark =
            when {
                data == null -> ABSENT
                data.contentEquals(INTERCEPTION_MARK) -> CURRENT
                else -> OTHER_FORMAT
            }
    }
}
