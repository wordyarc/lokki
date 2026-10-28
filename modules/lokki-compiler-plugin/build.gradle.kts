import lokki.buildlogic.COMPILER_PLUGIN_ID
import lokki.buildlogic.DEBUG_OPTION
import lokki.buildlogic.LOKKI_ABI_VERSION
import lokki.buildlogic.MOCKABLE_OPTION

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
    buildConfigField("MOCKABLE_OPTION", MOCKABLE_OPTION)
    buildConfigField("DEBUG_OPTION", DEBUG_OPTION)
    buildConfigField("LOKKI_ABI_VERSION", LOKKI_ABI_VERSION)
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
