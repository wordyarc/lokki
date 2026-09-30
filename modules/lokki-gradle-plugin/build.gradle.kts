import lokki.buildlogic.pluginId

plugins {
    id("lokki.kotlin-jvm")
    `java-gradle-plugin`
    id("com.github.gmazzo.buildconfig")
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
}

buildConfig {
    packageName("org.jetbrains.kotlin.compiler.plugin.template")

    buildConfigField("KOTLIN_PLUGIN_ID", pluginId)
    buildConfigField("KOTLIN_PLUGIN_GROUP", group.toString())
    buildConfigField("KOTLIN_PLUGIN_NAME", "lokki-compiler-plugin")
    buildConfigField("KOTLIN_PLUGIN_VERSION", version.toString())
    buildConfigField("ANNOTATIONS_LIBRARY_COORDINATES", "$group:lokki-plugin-annotations:$version")
}

gradlePlugin {
    plugins {
        create("SimplePlugin") {
            id = pluginId
            displayName = "SimplePlugin"
            description = "SimplePlugin"
            implementationClass = "org.jetbrains.kotlin.compiler.plugin.template.SimpleGradlePlugin"
        }
    }
}
