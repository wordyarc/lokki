check(!pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
    "lokki: ${project.path} applies lokki.host-stdlib after the Kotlin plugin, which has already added kotlin-stdlib"
}

extra["kotlin.stdlib.default.dependency"] = "false"
