package io.lokki.internal

import kotlin.reflect.KClass

internal actual fun identityHashCode(instance: Any): Int = System.identityHashCode(instance)

internal actual fun GeneratedMock.mocksClass(type: Any): Boolean =
    type.jvmClass?.let { javaClass.superclass === it } == true

internal actual fun GeneratedMock.redirectsFile(type: Any): Boolean {
    val facade = type.jvmClass?.name ?: return false
    return javaClass.interfaces.any { redirect ->
        val part = redirect.name.removeSuffix(LokkiAbi.FILE_REDIRECT_SUFFIX)
        part != redirect.name && (part == facade || part.startsWith("${facade}__"))
    }
}

private val Any.jvmClass: Class<*>?
    get() = when (this) {
        is KClass<*> -> java
        is Class<*> -> this
        else -> null
    }
