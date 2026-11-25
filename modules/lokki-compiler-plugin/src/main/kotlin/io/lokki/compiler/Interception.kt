package io.lokki.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal val REDIRECT = Name.identifier("LokkiRedirect")
internal val REDIRECT_PROPERTY = Name.identifier("lokki\$redirect")
internal const val FILE_REDIRECT_SUFFIX = BuildConfig.FILE_REDIRECT_SUFFIX
internal val CONSTRUCTORS = Name.identifier("LokkiConstructors")
internal val CONSTRUCTORS_HOLDER = Name.identifier("LokkiConstructorsHolder")

internal const val INTERCEPTION_POINTS_KOTLIN = "2.4.20"

internal val COMPOSABLE_ANNOTATION = ClassId.fromString("androidx/compose/runtime/Composable")

internal enum class InterceptionExclusion(val reason: String) {
    CONSTANT("it is a constant, which is inlined at compile time"),
    LATEINIT("it is a lateinit property, whose getter lokki does not intercept"),
    JVM_FIELD("it is a @JvmField property, which is read from its field without a getter"),
    TYPE_PARAMETERS("it is a property with type parameters, which lokki does not intercept"),
    INLINE("it is inline"),
    EXTERNAL("it is external and has no body to intercept"),
    COMPOSABLE("it is @Composable, whose body lokki leaves to the Compose compiler"),
}

internal val INTERCEPTION_MARK = byteArrayOf(1)

internal val REIFIED_HELPER_MARK = byteArrayOf(2)

internal enum class InterceptionMark {
    CURRENT,
    OTHER_FORMAT,
    ABSENT;

    companion object {
        fun of(data: ByteArray?): InterceptionMark =
            when {
                data == null || data.firstOrNull() == REIFIED_HELPER_MARK[0] -> ABSENT
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
