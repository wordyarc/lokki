plugins {
    kotlin("jvm") version libs.versions.kotlin
    id("io.lokki")
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useKotlinTest(libs.versions.kotlin)

            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
