package io.lokki.internal

import kotlin.reflect.KClass

@PublishedApi
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ExternalMockField(val factory: KClass<out ExternalMockFactory>, val index: Int)

@PublishedApi
internal interface ExternalMockFactory {
    fun `lokki$create`(index: Int, name: String, delegate: Any?): Any
}
