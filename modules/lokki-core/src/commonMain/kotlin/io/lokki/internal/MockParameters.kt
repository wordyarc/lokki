package io.lokki.internal

import kotlin.reflect.KClass

@PublishedApi
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class MockParameter(val factory: KClass<out MockParameterFactory>, val index: Int)

@PublishedApi
internal interface MockParameterFactory {
    fun `lokki$create`(index: Int, values: MockParameterValues): Any?

    fun `lokki$inject`(index: Int, instance: Any?, values: MockParameterValues)
}

@PublishedApi
internal class MockParameterValues(private val factory: MockParameterFactory) {
    private val values = HashMap<Int, Any?>()

    operator fun get(index: Int): Any? {
        if (index in values) return values[index]
        val value = factory.`lokki$create`(index, this)
        values[index] = value
        factory.`lokki$inject`(index, value, this)
        return value
    }
}
