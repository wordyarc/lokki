package io.lokki.internal

import io.lokki.MethodDescription

@PublishedApi
internal object MethodDescriptions {
    @JvmStatic
    fun of(
        name: String,
        returnType: Class<*>,
        flags: Int,
        declaringClass: Class<*>,
        paramTypes: Array<Class<*>>,
        varArgsArg: Int,
    ): MethodDescription =
        MethodDescription(name, returnType.kotlin, flags, declaringClass.kotlin, paramTypes.map { it.kotlin }, varArgsArg)
}
