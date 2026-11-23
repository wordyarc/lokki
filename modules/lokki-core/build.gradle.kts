import lokki.buildlogic.FILE_REDIRECT_SUFFIX
import lokki.buildlogic.LOKKI_ABI_FLAGS
import lokki.buildlogic.LOKKI_ABI_VERSION
import lokki.buildlogic.PRIVATE_HOOK_INFIX

plugins {
    id("lokki.kotlin-multiplatform")
    id("lokki.publish")
    id("com.github.gmazzo.buildconfig")
}

description = "Mocking for Kotlin without reflection or runtime code generation: the mockk-style DSL, matchers, " +
    "verification, scopes and the JUnit 5 extension"

buildConfig {
    packageName("io.lokki.internal")
    className("LokkiAbi")
    buildConfigField("VERSION", LOKKI_ABI_VERSION)
    buildConfigField("FILE_REDIRECT_SUFFIX", FILE_REDIRECT_SUFFIX)
    buildConfigField("PRIVATE_HOOK_INFIX", PRIVATE_HOOK_INFIX)
    LOKKI_ABI_FLAGS.forEach { (name, flag) -> buildConfigField(name, flag) }
}

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }

    sourceSets {
        jvmMain.dependencies {
            compileOnly(libs.junit.jupiter.api)
        }
    }
}

dependencies {
    constraints {
        "jvmMainApi"(libs.junit.jupiter.api)
    }
}
