package lokki.buildlogic

import org.gradle.api.Project

val Project.pluginId: String
    get() = providers.gradleProperty("lokki.plugin.id").get()
