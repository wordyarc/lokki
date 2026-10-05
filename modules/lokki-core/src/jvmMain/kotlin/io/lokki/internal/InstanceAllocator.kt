package io.lokki.internal

import io.lokki.LokkiException

@PublishedApi
internal object InstanceAllocator {
    private val unsafe = runCatching(::Unsafe)

    fun <T> allocate(type: Class<T>): T {
        val unsafe = unsafe.getOrElse { e ->
            throw LokkiException(
                "a mock of ${type.superclass.name} cannot be created without sun.misc.Unsafe on this JVM: " +
                    "mock an interface instead",
                e,
            )
        }
        return type.cast(unsafe.allocateInstance(type))
    }
}

private class Unsafe {
    private val unsafeClass = Class.forName("sun.misc.Unsafe")
    private val instance = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)

    fun allocateInstance(type: Class<*>): Any = allocateInstance.invoke(instance, type)
}
