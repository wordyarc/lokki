import lokki.buildlogic.LOKKI_ABI_VERSION

plugins {
    id("lokki.kotlin-multiplatform")
    id("lokki.publish")
    id("com.github.gmazzo.buildconfig")
}

buildConfig {
    packageName("io.lokki.internal")
    className("LokkiAbi")
    buildConfigField("VERSION", LOKKI_ABI_VERSION)
}

kotlin {
    compilerOptions {
        optIn.add("io.lokki.InternalLokkiApi")
    }
}
