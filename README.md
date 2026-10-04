# lokki

A mocking library for Kotlin with a MockK-like DSL, built on a K2 compiler plugin (FIR + IR).
A mock is an ordinary class generated at compile time, so tests need no reflection over user types, no proxies and
no JVM instrumentation.

**Status: early development.** The plugin generates a class for every `mock<T>()` of an interface, one class per
source file, and keeps incremental compilation correct when the mocked type changes. Stubbing is a skeleton:
`every { mock.member(...) } returns value` with `any()`, plain values and omitted default arguments. A member without
a matching stub throws `LokkiException`; other matchers, answers and verification are not implemented yet.

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.lokki") version "<version>"
}
```

```kotlin
val car = mock<Car>()
every { car.drive(any()) } returns "done"
```

The Gradle plugin applies the compiler plugin to the `test` and `testFixtures` compilations and to the source sets of
JVM test suites, and adds `lokki-core` to their dependencies.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| [`modules/lokki-core`](modules/lokki-core) | `io.lokki:lokki-core` | Runtime: the public DSL and the support for generated code. |
| [`modules/lokki-compiler-plugin`](modules/lokki-compiler-plugin) | `io.lokki:lokki-compiler-plugin` | Compiler plugin `io.lokki.compiler`: collects `mock<T>()` calls, generates mock classes and lowers the DSL. |
| [`modules/lokki-gradle-plugin`](modules/lokki-gradle-plugin) | `io.lokki:lokki-gradle-plugin` | Gradle plugin `io.lokki`. |
| [`modules/compiler-tests`](modules/compiler-tests) | not published | Compiler tests on the [Kotlin compiler test framework][test-framework]. |
| [`integration-testing`](integration-testing) | not published | A composite build that tests the real Gradle plugin end to end. |

## Build and tests

The build uses JDK toolchains: 25 to compile and to run tests, 17 to run the Gradle plugin tests on the minimal
supported Gradle (8.11.1). Gradle downloads the missing ones.

```
./gradlew build                                    # everything in the main build, including the ABI check
./gradlew :compiler-tests:test --tests '*MyTest*'  # compiler tests
./gradlew :lokki-gradle-plugin:functionalTest      # Gradle TestKit tests, including incremental compilation
./gradlew -p integration-testing check             # end-to-end tests with the real Gradle plugin
./gradlew updateKotlinAbi                          # update the ABI dumps after a public API change
```

The build cache is enabled, so add `--rerun` to run tests again without changes.

To add a compiler test, put a `.kt` file into [`modules/compiler-tests/src/test/data`](modules/compiler-tests/src/test/data):
`box` for codegen tests (`fun box(): String` must return `"OK"`) and `diagnostics` for frontend tests.
The JUnit classes are generated into `build/generated/tests` on the next test run.
The [Kotlin Compiler DevKit][test-plugin] IntelliJ plugin, pre-configured in this repository, helps to run them.

[//]: # (Links)

[test-framework]: https://github.com/JetBrains/kotlin/blob/master/compiler/test-infrastructure/ReadMe.md
[test-plugin]: https://github.com/JetBrains/kotlin-compiler-devkit
