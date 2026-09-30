package lokki.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal fun Project.version(alias: String): String =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion(alias)
        .orElseThrow { IllegalStateException("no version '$alias' in the version catalog") }
        .requiredVersion
