package io.lokki

import io.lokki.internal.LokkiAbi
import kotlin.reflect.KClass

public class MethodDescription internal constructor(
    public val name: String,
    public val returnType: KClass<*>,
    private val flags: Int,
    public val declaringClass: KClass<*>,
    public val paramTypes: List<KClass<*>>,
    public val varArgsArg: Int,
) {
    public val returnTypeNullable: Boolean
        get() = flags and RETURN_TYPE_NULLABLE != 0

    public val returnsUnit: Boolean
        get() = flags and RETURNS_UNIT != 0

    public val returnsNothing: Boolean
        get() = flags and RETURNS_NOTHING != 0

    public val isSuspend: Boolean
        get() = flags and SUSPEND != 0

    public val isFnCall: Boolean
        get() = flags and FN_CALL != 0

    public val privateCall: Boolean
        get() = flags and PRIVATE_CALL != 0

    internal val hasDefaultField: Boolean
        get() = flags and NO_DEFAULT_FIELD == 0

    public fun argsToStr(): String = paramTypes.joinToString(", ") { argToStr(it).toString() }

    public fun argToStr(argType: KClass<*>): String? = argType.simpleName

    public fun isToString(): Boolean = name == "toString" && paramTypes.isEmpty()

    public fun isHashCode(): Boolean = name == "hashCode" && paramTypes.isEmpty()

    public fun isEquals(): Boolean = name == "equals" && paramTypes.size == 1 && paramTypes[0] == Any::class

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is MethodDescription &&
            name == other.name &&
            returnType == other.returnType &&
            declaringClass == other.declaringClass &&
            paramTypes == other.paramTypes

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + declaringClass.hashCode()
        result = 31 * result + paramTypes.hashCode()
        return result
    }

    override fun toString(): String = "$name(${argsToStr()})"

    internal fun signature(): String {
        val returnType = if (returnsNothing) "Nothing" else returnType.simpleName + if (returnTypeNullable) "?" else ""
        return "${declaringClass.simpleName}.$this: $returnType"
    }
}

internal const val RETURN_TYPE_NULLABLE = LokkiAbi.METHOD_RETURN_TYPE_NULLABLE
internal const val RETURNS_UNIT = LokkiAbi.METHOD_RETURNS_UNIT
internal const val RETURNS_NOTHING = LokkiAbi.METHOD_RETURNS_NOTHING
internal const val SUSPEND = LokkiAbi.METHOD_SUSPEND
internal const val FN_CALL = LokkiAbi.METHOD_FN_CALL
internal const val NO_DEFAULT_FIELD = LokkiAbi.METHOD_NO_DEFAULT_FIELD
internal const val PRIVATE_CALL = LokkiAbi.METHOD_PRIVATE_CALL
