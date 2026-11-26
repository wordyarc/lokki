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
`verify { }` compile into calls of the runtime instead of being executed. lokki keeps the DSL of MockK, not every
behaviour of its runtime: what MockK does with a Java agent either fails compilation with an alternative or
[behaves differently](#coming-from-mockk).

- **No instrumentation.** No Java agent, proxies or runtime class generation.
- **Errors at compile time.** Unmockable types, calls that cannot be intercepted and stubs without an answer fail the
  build.
- **Scoped state.** `mockObject`, `mockStatic` and stubs added to shared mocks last until their scope — a JUnit 5
  test or `lokkiScope { }` — closes.

**Status:** alpha. Until 1.0, the API may change without a deprecation cycle, and mocks compiled by an older lokki
may be rejected by a newer runtime: recompile test fixtures and test libraries when upgrading.

## Requirements

- Kotlin 2.4.0 – 2.5.0-RC; `mockObject` and `mockStatic` need 2.4.20 or newer.
- Gradle 9.0 or newer.
- JVM target 17 or newer for test code.
- JUnit Jupiter 5.12 or newer, including 6.x, for `LokkiExtension`; lokki raises an older JUnit 5 to 5.12.2.

## Installation

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    id("io.github.wordyarc.lokki") version "0.3.0"
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
even if the code under test caught the exception. This covers the mocks created in the test and the mocks it uses
that were created outside any scope, such as the arguments of a `@MethodSource`.

`LokkiExtension` runs every test class, test and dynamic test in its own scope and initializes `@Mock`,
`@RelaxedMock`, `@Spy` and `@InjectMocks` properties:

```kotlin
class JobProcessorTest {
    @Mock
    lateinit var queue: JobQueue

    @Test
    fun skipsEmptyQueue() {
        every { queue.poll() } returns null

        assertFalse(JobProcessor(queue).runNext())
        verify { queue.poll() }
        confirmVerified()
    }
}
```

The properties file from [Installation](#installation) registers the extension for all tests, and
`@ExtendWith(LokkiExtension::class)` for one class. `@LokkiExtension.ConfirmVerification` and
`@LokkiExtension.CheckUnnecessaryStub` on a test class run `confirmVerified()` and `checkUnnecessaryStub()` after each
test. `@LokkiExtension.KeepMocks` on a test class or method keeps the stubs and recorded calls of a test for the rest
of the class.

Without JUnit 5, `lokkiScope { }` provides the scope and `MockAnnotations.init(this)` initializes the properties.
Outside a scope, mocks, stubs and verification work, but nothing is removed or checked when a test ends. Calls that
change global state or act on all mocks of a test throw `LokkiException` there: `mockObject`, `mockStatic`,
`mockConstructor`, stubs of objects and top-level functions, and `confirmVerified()`, `checkUnnecessaryStub()` and
`clearAllMocks()` without arguments.

### Final classes, objects and top-level functions

Without an agent, only calls of open members can be intercepted. Declarations of the module's main code that match a
`mockable` pattern are compiled differently: classes and their members are open in the bytecode, and `object`s and
top-level functions get interception points. A pattern is a package with its subpackages (`com.example.**`), a class,
an annotation or a function.

```kotlin
// build.gradle.kts
lokki {
    mockable("com.example.**")
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
`mockStatic("com.example.FileKt")` for a whole file. An overloaded or generic function cannot be passed by reference:
Kotlin does not resolve it. `mockConstructor(Foo::class)` with `anyConstructed<Foo>()` intercepts constructor calls in
code compiled with the plugin. Private functions of these classes are stubbed and verified by name:
`every { spy["name"]() }`.

Only the bytecode changes, so smart casts and initialization rules of main code stay as they are. Sealed classes stay
sealed with their members open, and inline and `tailrec` functions and `@JvmField` properties stay final. Every call of an intercepted
function or constructor reads a volatile field, and the plugin warns if the project applies `maven-publish`.

## Configuration

| `lokki { }` setting | Effect |
|---|---|
| `mockable(vararg patterns)` | Makes final classes, `object`s and top-level functions of main code mockable, see [above](#final-classes-objects-and-top-level-functions). |
| `restricted(vararg patterns)` | Adds types whose mocks get the warning `LOKKI_RESTRICTED_TYPE`, as collections, maps, `File`, `Path` and types annotated with `@DoNotMock` do. A pattern is a package with its subpackages, a class or an annotation. |
| `testCompilations(vararg names)` | Applies the plugin to test compilations it does not recognize, such as a source set created without `jvm-test-suite`. |
| `applyToMain = true` | Lets main code create mocks, for modules with test utilities: applies the plugin and adds the runtime to main code. |
| `recordStackTraces = true` | Adds call sites of mock calls to failure reports. |

| Gradle property | Effect |
|---|---|
| `lokki.recordStackTraces=true` | Turns `recordStackTraces` on unless the build script sets it, for example with `-P` for one run. |
| `lokki.debug=true` | Reports the code of every file that lokki changes at the `--info` log level, for bug reports. |
| `lokki.mockablePublication.nowarn=true` | Disables the warning about `mockable` in published artifacts and non-debug Android variants. |
| `lokki.kotlinCompatibility.nowarn=true` | Disables the warning about a Kotlin version newer than the tested ones. |

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

Behaviour that differs:

- The receiver and arguments of the call in `every { }` and `verify { }` are evaluated once, as ordinary code, and
  `any()` never creates a placeholder value.
- A call chain such as `every { car.door().open() }` stubs `car.door()` with a child mock only when the call has no
  answer yet; a stubbed `car.door()` keeps its answer. Relaxed mocks return child mocks only if the declaration that
  creates them — a file, class, function, property or local variable — is annotated with `@ChainedMocks`: lokki
  generates their classes at compile time, so the annotation marks where that cost is paid.
- A relaxed mock returns `null` for nullable types and for a Java type parameter that allows it, such as
  `<T> T query(...)`, empty values for primitives, strings, collections, streams, arrays and `Optional`, and the
  `ZERO` or `EPOCH` of a type that declares one, such as `Duration.ZERO`. Any other return type throws
  `LokkiException`.
- Spies are not copies: `spy(Foo(args))` constructs the spy itself, and `spy(existing)` forwards calls to `existing`,
  so the calls that `existing` makes on itself bypass stubs and are not recorded.
- A stub of a property does not write its backing field: MockK copies the value of `returns` into the field. Code that
  reads fields by reflection, such as Bean Validation of `@field:` constraints, needs a real object or
  `spy(Foo(args))`.
- Calls of `equals`, `hashCode` and `toString` are not recorded.
- `mock.javaClass` is a generated subclass of the mocked class, not the class itself.
- `coVerify` does not run coroutines that are still waiting to be dispatched: MockK runs them as a side effect of its
  `runBlocking`. Start them with `CoroutineStart.UNDISPATCHED` or let them complete before verifying.

## Limitations

- JVM only: Kotlin/JVM, Android and the JVM target of multiplatform projects. Android instrumented tests compile with
  the plugin but have not been run on ART.
- Mocks are created and stubbed only in Kotlin code compiled with the plugin; Java code can only call them.
- The mocked type must be known at compile time: `mockClass` takes a class literal or a reified `T::class`, not a
  `KClass` from a variable. A block passed to `every` or `verify` through your own function is an error.
- Final classes, objects, functions and constructors from libraries and Java static methods cannot be mocked. Pass
  them to the code under test instead, for example a `kotlin.time.Clock` or a `() -> UUID` in place of
  `Instant.now()` and `UUID.randomUUID()`, and give tests a fake or a mock. Where the code cannot take a parameter,
  wrap the call in a top-level function of main code that matches a `mockable` pattern, for example
  `fun newId(): UUID = UUID.randomUUID()`, and stub that function.
- A JDK dynamic proxy of a mock, such as an interface-based Spring AOP proxy, is not a mock: `every` and `verify` need
  the mock itself or a class proxy (`proxyTargetClass = true`). Class proxies of mocks work, including Spring's method
  validation.
- No JUnit 4 integration: use `lokkiScope { }`. Kotest is not supported.
- IntelliJ IDEA shows `LOKKI_*` diagnostics only with the registry key
  `kotlin.k2.only.bundled.compiler.plugins.enabled` set to `false`; the Gradle build always reports them.

## Development

```shell
./gradlew build                                   # all tests and checks of the main build, including the ABI check
./gradlew -p integration-testing check            # end-to-end tests and the MockK conformance suite
./gradlew :compiler-tests:test --tests '*Name*'   # selected compiler plugin tests
./gradlew updateKotlinAbi                         # update the ABI dumps after a public API change
./gradlew :benchmarks:jmh                         # JMH benchmarks of mock creation, calls and verification
```

Gradle downloads the JDK 25 toolchain of the build if it is missing. The Gradle plugin tests in `build` need an
Android SDK (`ANDROID_HOME`). The tests of `lokki-maven` download Maven and keep its local repository in
`modules/lokki-maven/build`.

## License

[Apache License 2.0](LICENSE.txt)
