package io.lokki.internal

import io.lokki.InjectMocks
import io.lokki.Mock
import io.lokki.OverrideMocks
import io.lokki.RelaxedMock
import java.lang.reflect.AnnotatedElement

internal actual fun uninitializedMockPropertiesOwner(target: Any): String? =
    try {
        generateSequence<Class<*>>(target.javaClass) { it.superclass }.firstOrNull { it.needsInitializer() }?.name
    } catch (_: LinkageError) {
        null
    }

private fun Class<*>.needsInitializer(): Boolean =
    declaredMethods.any { it.isSynthetic && it.isMock() } &&
        declaredConstructors.none { constructor -> constructor.parameterAnnotations.any { it.any(::isMock) } }

private fun AnnotatedElement.isMock(): Boolean = annotations.any(::isMock)

private fun isMock(annotation: Annotation): Boolean =
    annotation is Mock || annotation is RelaxedMock || annotation is InjectMocks || annotation is OverrideMocks
