<h1>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/logo/lokki-wordmark-dark.svg">
    <img src="docs/logo/lokki-wordmark.svg" alt="lokki" height="72">
  </picture>
</h1>

[![Build](https://github.com/wordyarc/lokki/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/wordyarc/lokki/actions/workflows/build.yml)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.wordyarc.lokki?label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.wordyarc.lokki)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.wordyarc.lokki/lokki-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.wordyarc.lokki/lokki-core)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE.txt)

lokki is a mocking library for Kotlin/JVM with the [MockK](https://mockk.io) DSL, implemented as a K2 compiler
plugin. `mock<T>()` compiles into an ordinary class that implements `T`, and the blocks of `every { }` and
`verify { }` compile into calls of the runtime instead of being executed.

- **No instrumentation.** No Java agent, proxies or runtime class generation.
- **Errors at compile time.** Unmockable types, calls that cannot be intercepted and stubs without an answer fail the
  build.
- **Scoped state.** `mockObject`, `mockStatic` and stubs added to shared mocks last until their scope — a JUnit 5
  test or `lokkiScope { }` — closes.

**Status:** alpha. Until 1.0, the API may change without a deprecation cycle, and mocks compiled by an older lokki
may be rejected by a newer runtime: recompile test fixtures and test libraries when upgrading.

## Requirements

- Kotlin 2.4.0 – 2.5.0-RC. Mocking objects, top-level functions, constructors and private functions needs 2.4.20 or
  newer.
- Gradle 9.0 or newer.
- JVM target 17 or newer for test code.
- JUnit Jupiter 5.12 or newer, including 6.x, for `LokkiExtension`; lokki raises an older JUnit 5 to 5.12.2.

## Installation

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.github.wordyarc.lokki") version "0.3.2"
}
```

The plugin applies to test compilations — `test`, `testFixtures`, JVM test suites, `jvmTest` of multiplatform
projects and Android test variants — and adds the runtime, `io.github.wordyarc.lokki:lokki-core`, to them. The
runtime includes the JUnit 5 extension. Register it for all tests in `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
junit.jupiter.extensions.autodetection.include=io.lokki.junit5.*
```

Every test then runs in its own [scope](#scopes).

## Usage

```kotlin
class Job(val id: String)

interface JobQueue {
    fun poll(): Job?
    fun complete(job: Job)
}

class JobProcessor(private val queue: JobQueue) {
    fun runNext(): Boolean {
        val job = queue.poll() ?: return false
        queue.complete(job)
        return true
    }
}
```

```kotlin
import io.lokki.Runs
import io.lokki.every
import io.lokki.just
import io.lokki.mock
import io.lokki.verify
import kotlin.test.Test
import kotlin.test.assertTrue

class JobProcessorTest {
    @Test
    fun completesPolledJob() {
        val queue = mock<JobQueue>()
        val job = Job("42")
        every { queue.poll() } returns job
        every { queue.complete(any()) } just Runs

        assertTrue(JobProcessor(queue).runNext())
        verify(exactly = 1) { queue.complete(job) }
    }
}
```

`mock<T>()` accepts interfaces, open and abstract classes and function types, including generic types and `suspend`
members. Matchers, answers, `coEvery`/`coVerify`, ordered verification, relaxed mocks and spies work as in MockK,
with the differences listed in [Coming from MockK](#coming-from-mockk).

Misuse fails compilation:

```kotlin
mock<Job>()
// e: 'com.example.Job' cannot be mocked: it is a final class. Extract an interface,
//    make the class and its members open, or add it to lokki { mockable(...) }.

every { queue.complete(any()) }
// e: 'every' registers nothing without an answer: add 'returns', 'answers', 'throws'
//    or 'just Runs' after the block.
```

### Scopes

A scope is the lifetime of what a test does to mocks. When it closes, the stubs and recorded calls added in the test
are removed, mocked objects, functions and constructors are restored, and the test fails if a mock call had no answer,
even if the code under test caught the exception.

`LokkiExtension` runs every test class, test and dynamic test in its own scope and initializes `@Mock`,
`@RelaxedMock`, `@Spy` and `@InjectMocks` properties. The properties file from [Installation](#installation)
registers it for all tests, and `@ExtendWith(LokkiExtension::class)` for one class. Without JUnit 5, `lokkiScope { }`
provides the scope and `MockAnnotations.init(this)` initializes the properties.

Outside a scope, mocks, stubs and verification work, but nothing is removed or checked when a test ends, and calls
that need a scope, such as `mockObject` and `mockStatic`, throw `LokkiException`.

### Final classes, objects and top-level functions

Without an agent, only calls of open members can be intercepted. Classes of the module's main code that match a
`mockable` pattern are open in the bytecode, together with their members. Declarations that match an `interceptable`
pattern also get interception points: `object`s, top-level functions and constructor calls. A pattern is a package with
its subpackages (`com.example.**`), a class, an annotation or, for `interceptable`, an object or a function.

```kotlin
// build.gradle.kts
lokki {
    mockable("com.example.**")
    interceptable("com.example.Clock")
}
```

```kotlin
// src/main/kotlin
object Clock {
    fun hour(): Int = 12
}
```

```kotlin
// src/test/kotlin, in a scope
@Test
fun morning() {
    every { Clock.hour() } returns 9
    // Clock is restored when the test's scope closes
}
```

`every` mocks the object in the current scope by itself; `mockObject(Clock)` records its calls for `verify` without
a stub. `mockStatic(::function)` does the same for top-level functions and properties, including extensions, and
`mockConstructor(Foo::class)` with `anyConstructed<Foo>()` intercepts constructor calls in code compiled with the
plugin.

Only the bytecode changes, so smart casts and initialization rules of main code stay as they are. Opening costs
nothing at run time, every call of an intercepted function or constructor reads a volatile field, and the plugin warns
if the project applies `maven-publish`.

## Coming from MockK

Functions, matchers and their parameters keep MockK's names, so the [MockK documentation](https://mockk.io) applies.
Imports move from `io.mockk` to `io.lokki`, including annotations from `io.mockk.impl.annotations`, and names that
contain `mockk` or `MockK` change:

| MockK | lokki |
|---|---|
| `mockk`, `spyk` | `mock`, `spy` |
| `mockkClass` | `mockClass` |
| `mockkObject`, `mockkStatic`, `mockkConstructor`, `unmockkAll` | `mockObject`, `mockStatic`, `mockConstructor`, `unmockAll` |
| `@MockK`, `@RelaxedMockK`, `@SpyK`, `@InjectMockKs` | `@Mock`, `@RelaxedMock`, `@Spy`, `@InjectMocks` |
| `MockKExtension`, `MockKAnnotations`, `MockKException` | `LokkiExtension`, `MockAnnotations`, `LokkiException` |
| `MockKMatcherScope` | `MatcherScope` |
| `mockk-bdd` | `lokki-bdd` |
| springmockk `@MockkBean`, `@MockkSpyBean`, `MockkClear` | `@MockBean`, `@SpyBean`, `MockClear` in `lokki-spring`, Spring 6.2.6+ |

Differences in behaviour include:

- The receiver and arguments of the call in `every { }` and `verify { }` are evaluated once, as ordinary code, and
  `any()` never creates a placeholder value.
- A relaxed mock returns `null` only for a nullable type, zero for numbers and amounts, `""` for strings and an empty
  value for containers. Any other return type throws `LokkiException`, and a child mock is returned only if the
  declaration that creates the relaxed mock is annotated with `@ChainedMocks`.
- Spies are not copies: `spy(Foo(args))` constructs the spy itself, and `spy(existing)` forwards calls to `existing`,
  so the calls that `existing` makes on itself bypass stubs and are not recorded.

## Limitations

- JVM only: Kotlin/JVM, Android and the JVM target of multiplatform projects. Android instrumented tests compile with
  the plugin; lokki's own tests do not run them on a device.
- Mocks are created and stubbed only in Kotlin code compiled with the plugin; Java code can only call them.
- The mocked type must be known at compile time: `mockClass` takes a class literal or a reified `T::class`, not a
  `KClass` from a variable. A block passed to `every` or `verify` through your own function is an error.
- Final classes, objects, functions and constructors from libraries and Java static methods cannot be mocked. Pass
  them to the code under test as a parameter, for example a `kotlin.time.Clock` or a `() -> UUID`, or wrap the call
  in a top-level function of main code that matches an `interceptable` pattern, for example
  `fun newId(): UUID = UUID.randomUUID()`, and stub that function.
- No JUnit 4 integration: use `lokkiScope { }`. Kotest is not supported.
- IntelliJ IDEA shows `LOKKI_*` diagnostics only with the registry key
  `kotlin.k2.only.bundled.compiler.plugins.enabled` set to `false`; the Gradle build always reports them.

## License

[Apache License 2.0](LICENSE.txt)
