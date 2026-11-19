package io.lokki.gradle

import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.kotlin.tooling.core.isDev
import org.jetbrains.kotlin.tooling.core.toKotlinVersion

internal enum class KotlinCompatibility {
    SUPPORTED,
    OLDER_THAN_SUPPORTED,
    NEWER_THAN_TESTED,
}

internal fun kotlinCompatibility(version: String, oldestSupported: String, latestTested: String): KotlinCompatibility {
    val used = kotlinVersionOrNull(version) ?: return KotlinCompatibility.SUPPORTED
    return when {
        used < KotlinToolingVersion(oldestSupported) -> KotlinCompatibility.OLDER_THAN_SUPPORTED
        used > KotlinToolingVersion(latestTested) -> KotlinCompatibility.NEWER_THAN_TESTED
        else -> KotlinCompatibility.SUPPORTED
    }
}

private fun kotlinVersionOrNull(version: String): KotlinToolingVersion? {
    val parsed = runCatching { KotlinToolingVersion(version) }.getOrNull() ?: return null
    return if (parsed.isDev) KotlinToolingVersion(parsed.toKotlinVersion()) else parsed
}
