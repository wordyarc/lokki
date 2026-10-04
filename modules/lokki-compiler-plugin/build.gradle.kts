import lokki.buildlogic.COMPILER_PLUGIN_ID

plugins {
    id("lokki.kotlin-jvm")
    id("lokki.publish")
    id("com.github.gmazzo.buildconfig")
}

dependencies {
    compileOnly(libs.kotlin.compiler)
}

buildConfig {
    packageName("io.lokki.compiler")
    buildConfigField("COMPILER_PLUGIN_ID", COMPILER_PLUGIN_ID)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
