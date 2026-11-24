package io.lokki.spring

import io.lokki.LokkiException
import io.lokki.internal.ExternalMocks
import io.lokki.isMock
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.SingletonBeanRegistry
import org.springframework.core.ResolvableType
import org.springframework.core.style.ToStringCreator
import org.springframework.test.context.bean.override.BeanOverrideHandler
import org.springframework.test.context.bean.override.BeanOverrideStrategy
import java.lang.reflect.Field
import java.util.Objects

internal abstract class LokkiBeanOverrideHandler(
    field: Field,
    type: ResolvableType,
    name: String,
    contextName: String,
    strategy: BeanOverrideStrategy,
    val clear: MockClear,
) : BeanOverrideHandler(field, type, name.ifBlank { null }, contextName, strategy) {
    protected abstract val settings: List<Any>

    override fun trackOverrideInstance(mock: Any, trackingBeanRegistry: SingletonBeanRegistry) {
        MockBeans.of(trackingBeanRegistry).add(mock, clear)
    }

    override fun equals(other: Any?): Boolean =
        other === this || other is LokkiBeanOverrideHandler && super.equals(other) && clear == other.clear &&
            settings == other.settings

    override fun hashCode(): Int = Objects.hash(super.hashCode(), clear, settings)

    override fun toString(): String =
        ToStringCreator(this)
            .append("field", field)
            .append("beanType", beanType)
            .append("beanName", beanName)
            .append("contextName", contextName)
            .append("strategy", strategy)
            .append("clear", clear)
            .append("settings", settings)
            .toString()
}

internal class MockBeanOverrideHandler(field: Field, type: ResolvableType, annotation: MockBean) :
    LokkiBeanOverrideHandler(
        field,
        type,
        annotation.name,
        annotation.contextName,
        if (annotation.enforceOverride) BeanOverrideStrategy.REPLACE else BeanOverrideStrategy.REPLACE_OR_CREATE,
        annotation.clear,
    ) {
    override val settings: List<Any> =
        listOf(annotation.extraInterfaces.toSet(), annotation.relaxed, annotation.relaxUnitFun)

    override fun createOverrideInstance(
        beanName: String,
        existingBeanDefinition: BeanDefinition?,
        existingBeanInstance: Any?,
    ): Any = ExternalMocks.mock(field!!, beanName)
}

internal class SpyBeanOverrideHandler(field: Field, type: ResolvableType, annotation: SpyBean) :
    LokkiBeanOverrideHandler(
        field,
        type,
        annotation.name,
        annotation.contextName,
        BeanOverrideStrategy.WRAP,
        annotation.clear,
    ) {
    override val settings: List<Any> = emptyList()

    override fun createOverrideInstance(
        beanName: String,
        existingBeanDefinition: BeanDefinition?,
        existingBeanInstance: Any?,
    ): Any {
        val bean = existingBeanInstance
            ?: throw LokkiException("@SpyBean needs an existing bean to wrap, but there is no bean $beanName")
        return if (isMock(bean)) bean else ExternalMocks.spy(field!!, beanName, bean)
    }
}
