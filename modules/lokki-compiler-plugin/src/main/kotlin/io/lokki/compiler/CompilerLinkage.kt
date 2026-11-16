package io.lokki.compiler

import io.lokki.compiler.compat.CompatLoadException
import io.lokki.compiler.compat.linkageBreak
import org.jetbrains.kotlin.config.KotlinCompilerVersion

internal const val PLUGIN = "lokki: compiler plugin ${BuildConfig.VERSION}"

internal fun unlinkedCompiler(): String =
    "Kotlin ${KotlinCompilerVersion.getVersion()} is not supported: the compiler plugin does not link against it. " +
        "The newest tested Kotlin release is ${BuildConfig.LATEST_TESTED_KOTLIN}."

internal inline fun <T> linked(block: () -> T): T = try {
    block()
} catch (failure: Throwable) {
    val broken = failure.linkageBreak ?: throw failure
    throw CompatLoadException("$PLUGIN failed. ${unlinkedCompiler()}", broken)
}
