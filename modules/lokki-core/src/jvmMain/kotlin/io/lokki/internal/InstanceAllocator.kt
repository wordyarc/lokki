package io.lokki.internal

import io.lokki.LokkiException
import sun.misc.Unsafe

@PublishedApi
internal object InstanceAllocator {
    private val unsafe = runCatching {
        Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
    }

    fun <T> allocate(type: Class<T>): T {
        val unsafe = unsafe.getOrElse { e ->
            throw LokkiException(
                "a mock of ${type.superclass.name} cannot be created without sun.misc.Unsafe on this JVM: " +
                    "mock an interface instead",
                e,
            )
        }
        return try {
            type.cast(unsafe.allocateInstance(type))
        } catch (e: LinkageError) {
            throw LokkiException("a mock of ${type.superclass.name} cannot be created: its class failed to initialize", e)
        }
    }
}
