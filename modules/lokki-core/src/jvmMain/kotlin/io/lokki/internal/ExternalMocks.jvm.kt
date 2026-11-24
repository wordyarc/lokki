package io.lokki.internal

import io.lokki.InternalLokkiApi
import io.lokki.LokkiException
import io.lokki.LokkiScope
import java.lang.ref.WeakReference
import java.lang.reflect.Field

@InternalLokkiApi
public object ExternalMocks {
    public fun isScoped(testInstance: Any): Boolean = ScopedTests.contains(testInstance)

    public fun mock(field: Field, name: String): Any = create(field, name, null)

    public fun spy(field: Field, name: String, instance: Any): Any = create(field, name, instance)

    private fun create(field: Field, name: String, delegate: Any?): Any {
        val annotation = field.getAnnotation(ExternalMockField::class.java) ?: throw LokkiException(
            "${field.declaringClass.name}.${field.name} was not processed by the lokki compiler plugin: apply the " +
                "plugin of the same version as lokki-core to the compilation of this test"
        )
        val factory = annotation.factory.java.getDeclaredConstructor().newInstance()
        return LokkiScope.withCurrent(LokkiScope.root) { factory.`lokki$create`(annotation.index, name, delegate) }
    }
}

internal object ScopedTests {
    private val instances = ArrayList<WeakReference<Any>>()

    fun add(instance: Any) {
        synchronized(instances) {
            instances.removeAll { it.get() == null }
            if (instances.none { it.get() === instance }) instances += WeakReference(instance)
        }
    }

    fun contains(instance: Any): Boolean = synchronized(instances) { instances.any { it.get() === instance } }
}
