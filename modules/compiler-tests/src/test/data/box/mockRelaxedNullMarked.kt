// FILE: org/jspecify/annotations/NullMarked.java
package org.jspecify.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface NullMarked {}

// FILE: org/jspecify/annotations/Nullable.java
package org.jspecify.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface Nullable {}

// FILE: sample/Templates.java
package sample;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface Templates {
    <T extends @Nullable Object> T nullableBound(List<T> rows);

    <T extends @Nullable CharSequence> T nullableTextBound();

    <T> T nonNullBound(List<T> rows);
}

// FILE: sample/DerivedTemplates.java
package sample;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface DerivedTemplates extends Templates {}

// FILE: test.kt
package sample

import io.lokki.LokkiException
import io.lokki.mock

interface KotlinGeneric {
    fun <T> value(): T
}

abstract class KotlinOverride : Templates {
    abstract override fun <T> nullableBound(rows: List<T>): T
}

private inline fun failure(block: () -> Unit): String =
    try {
        block()
        ""
    } catch (exception: LokkiException) {
        exception.message.orEmpty()
    }

private const val NO_DEFAULT = "relaxed mock has no default for type parameter T"

fun box(): String {
    val templates = mock<Templates>(relaxed = true)
    if (templates.nullableBound<String?>(listOf("a")) != null) return "Fail nullable bound"
    if (templates.nullableTextBound<CharSequence?>() != null) return "Fail nullable class bound"
    if (NO_DEFAULT !in failure { templates.nonNullBound(listOf("a")) }) return "Fail non-null bound"

    val derived = mock<DerivedTemplates>(relaxed = true)
    if (derived.nullableBound<String?>(listOf("a")) != null) return "Fail inherited nullable bound"
    if (NO_DEFAULT !in failure { derived.nonNullBound(listOf("a")) }) return "Fail inherited non-null bound"

    val generic = mock<KotlinGeneric>(relaxed = true)
    if (NO_DEFAULT !in failure { generic.value<String?>() }) return "Fail Kotlin type parameter"

    val override = mock<KotlinOverride>(relaxed = true)
    if (NO_DEFAULT !in failure { override.nullableBound<String?>(listOf("a")) }) return "Fail Kotlin override"
    if (override.nullableTextBound<CharSequence?>() != null) return "Fail member inherited from Java"
    return "OK"
}
