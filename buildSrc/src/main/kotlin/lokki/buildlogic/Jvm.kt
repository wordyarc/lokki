package lokki.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

internal fun Project.targetJvm(compilerOptions: KotlinJvmCompilerOptions, target: String) {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(version("jvm-toolchain")))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(JavaVersion.toVersion(target).majorVersion.toInt())
    }
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(target))
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=$target")
}
