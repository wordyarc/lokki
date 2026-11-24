package io.lokki.spring

import io.lokki.LokkiException
import org.springframework.core.ResolvableType
import org.springframework.core.SpringVersion
import org.springframework.test.context.bean.override.BeanOverrideHandler
import org.springframework.test.context.bean.override.BeanOverrideProcessor
import java.lang.reflect.Field

internal class MockBeanOverrideProcessor : BeanOverrideProcessor {
    override fun createHandler(overrideAnnotation: Annotation, testClass: Class<*>, field: Field): BeanOverrideHandler {
        checkSpringVersion()
        val type = ResolvableType.forField(field, testClass)
        return when (overrideAnnotation) {
            is MockBean -> MockBeanOverrideHandler(field, type, overrideAnnotation)
            is SpyBean -> SpyBeanOverrideHandler(field, type, overrideAnnotation)
            else -> throw LokkiException(
                "MockBeanOverrideProcessor handles @MockBean and @SpyBean, not $overrideAnnotation on " +
                    "${field.declaringClass.name}.${field.name}"
            )
        }
    }
}

private fun checkSpringVersion() {
    val version = SpringVersion.getVersion() ?: return
    val numbers = Regex("\\d+").findAll(version).take(3).map { it.value.toInt() }.toList()
    if (numbers.size == 3 && KotlinVersion(numbers[0], numbers[1], numbers[2]) < MIN_SPRING) {
        throw LokkiException("lokki-spring requires Spring Framework $MIN_SPRING or newer, but the test runs on $version")
    }
}

private val MIN_SPRING = KotlinVersion(6, 2, 6)
