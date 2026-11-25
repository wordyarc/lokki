plugins {
    id("lokki.kotlin-jvm")
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks of lokki mocks: creation, stubbed calls and verification"

dependencies {
    jmhImplementation(project(":lokki-core"))
    "kotlinCompilerPluginClasspathJmh"(project(":lokki-compiler-plugin"))
}

jmh {
    warmupIterations = 5
    iterations = 5
    warmup = "1s"
    timeOnIteration = "1s"
    fork = 2
    profilers = listOf("gc")
}

tasks.check {
    dependsOn(tasks.jmhJar)
}
